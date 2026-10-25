package zoro.benojir.callrecorder.activities;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SearchView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import zoro.benojir.callrecorder.R;
import zoro.benojir.callrecorder.adapters.RecordingsListAdapter;
import zoro.benojir.callrecorder.helpers.AppLock;
import zoro.benojir.callrecorder.helpers.CustomFunctions;
import zoro.benojir.callrecorder.helpers.RecorderHelper;
import zoro.benojir.callrecorder.helpers.RecordingCategory;
import zoro.benojir.callrecorder.services.RecordingService;
import zoro.benojir.callrecorder.services.RecordingSessions;

public class MainActivity extends LockedActivity {
    public static MenuItem searchBtn, settingsBtn, selectedItemsCountMenu;
    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private RecordingsListAdapter adapter;
    private SharedPreferences preferences;
    private String category = "all";
    private String search = "";
    private int loadGeneration;
    private boolean permissionsAsked;
    private final BroadcastReceiver changes = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { if (!AppLock.needsUnlock(MainActivity.this)) loadRecordings(); }
    };
    @Override protected void onCreate(Bundle state) {
        SettingsActivity.applyTheme(this);
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        permissionsAsked = state != null && state.getBoolean("permissions_asked");
        if (state != null) category = state.getString("category", "all");
        MaterialToolbar toolbar = findViewById(R.id.libraryToolbar);
        setSupportActionBar(toolbar);
        RecyclerView list = findViewById(R.id.recyclerView);
        list.setLayoutManager(new LinearLayoutManager(this));
        ChipGroup chips = findViewById(R.id.categoryChips);
        List<String> categories = new ArrayList<>();
        categories.add("all"); categories.addAll(Arrays.asList(RecordingCategory.VALUES));
        for (String value : categories) {
            Chip chip = new Chip(this);
            chip.setId(View.generateViewId()); chip.setCheckable(true);
            chip.setText("all".equals(value) ? "All" : RecordingCategory.label(value));
            chip.setChecked(category.equals(value));
            chips.addView(chip);
            chip.setOnCheckedChangeListener((button, checked) -> {
                if (checked) { category = value; applyFilter(); }
            });
        }
        findViewById(R.id.setupRecorder).setOnClickListener(v -> openSettings());
        findViewById(R.id.recordNow).setOnClickListener(v -> manualRecording());
        findViewById(R.id.pauseRecording).setOnClickListener(v -> control(RecordingService.UI_PAUSE));
        findViewById(R.id.stopRecording).setOnClickListener(v -> control(RecordingService.UI_STOP));
        ContextCompat.registerReceiver(this, changes, new IntentFilter(RecordingService.CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED);
    }
    @Override protected void onAppUnlocked() {
        if (preferences == null) return;
        if (!permissionsAsked || getIntent().getBooleanExtra("request_permissions", false)) {
            permissionsAsked = true;
            getIntent().removeExtra("request_permissions");
            requestPermissionsIfNeeded();
        }
        loadRecordings();
    }
    private void requestPermissionsIfNeeded() {
        List<String> needed = new ArrayList<>();
        for (String permission : new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_CONTACTS, Manifest.permission.READ_PHONE_STATE})
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) needed.add(permission);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.POST_NOTIFICATIONS);
        if (!needed.isEmpty()) ActivityCompat.requestPermissions(this, needed.toArray(new String[0]), 4528);
    }
    private void manualRecording() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 4528); return;
        }
        String[] names = new String[RecordingCategory.VALUES.length];
        for (int i = 0; i < names.length; i++) names[i] = RecordingCategory.label(RecordingCategory.VALUES[i]);
        new AlertDialog.Builder(this).setTitle("Record now · choose category")
                .setItems(names, (d, index) -> RecordingSessions.begin(this, RecordingCategory.VALUES[index], "manual"))
                .setNegativeButton("Cancel", null).show();
    }
    private void control(String action) {
        String id = RecordingService.activeSession();
        if (id != null) startService(RecordingSessions.command(this, action, id));
    }
    private void updateStatus() {
        boolean recording = RecordingService.activeSession() != null;
        findViewById(R.id.recordNow).setVisibility(recording ? View.GONE : View.VISIBLE);
        findViewById(R.id.pauseRecording).setVisibility(recording ? View.VISIBLE : View.GONE);
        findViewById(R.id.stopRecording).setVisibility(recording ? View.VISIBLE : View.GONE);
        ((MaterialButton) findViewById(R.id.pauseRecording)).setText(RecordingService.isPaused() ? R.string.resume_recording : R.string.pause_recording);
        String status = recording ? (RecordingService.isPaused() ? "Recording paused" : "Recording in progress")
                : RecorderHelper.hasCallAudioPermission(this) ? "Privileged audio available · Configure in Settings"
                : CustomFunctions.isSystemApp(this) ? "System app · Check protected audio permission in Settings"
                : "Microphone mode · Set up accessibility in Settings";
        ((TextView) findViewById(R.id.recorderStatus)).setText(status);
    }
    private void loadRecordings() {
        updateStatus();
        final int generation = ++loadGeneration;
        final String sort = preferences.getString("sort_by", "sort_by_new");
        findViewById(R.id.libraryProgress).setVisibility(adapter == null ? View.VISIBLE : View.GONE);
        loader.execute(() -> {
            List<File> files = new ArrayList<>();
            File external = getExternalFilesDir("recordings");
            if (external != null) scan(external, files);
            scan(new File(getFilesDir(), "recordings"), files);
            Comparator<File> ordering = "sort_by_old".equals(sort) ? Comparator.comparingLong(File::lastModified)
                    : "sort_by_name_ascending".equals(sort) ? Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER)
                    : "sort_by_name_descending".equals(sort) ? Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER).reversed()
                    : Comparator.comparingLong(File::lastModified).reversed();
            files.sort(ordering);
            JSONArray items = new JSONArray();
            long bytes = 0;
            for (File file : files) {
                try {
                    String folder = file.getParentFile().getName();
                    String source = "recordings".equals(folder) ? "phone" : RecordingCategory.safeCategory(folder);
                    JSONObject item = new JSONObject();
                    item.put("file_name", file.getName());
                    item.put("display_name", file.getName().replaceFirst("_\\d{4}-\\d{2}-\\d{2}.*$", "").replaceFirst("\\.(m4a|amr)$", ""));
                    item.put("category", source);
                    item.put("date_group", DateFormat.getDateInstance(DateFormat.MEDIUM).format(new Date(file.lastModified())));
                    item.put("size", CustomFunctions.fileSizeFormatter(file.length()));
                    item.put("bytes", file.length());
                    item.put("modified_date", CustomFunctions.timeFormatter(file.lastModified()));
                    item.put("absolute_path", file.getAbsolutePath());
                    items.put(item); bytes += file.length();
                } catch (Exception ignored) { }
            }
            final long totalBytes = bytes;
            runOnUiThread(() -> {
                if (isDestroyed() || generation != loadGeneration) return;
                adapter = new RecordingsListAdapter(this, items);
                ((RecyclerView) findViewById(R.id.recyclerView)).setAdapter(adapter);
                adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
                    @Override public void onChanged() { emptyState(); }
                    @Override public void onItemRangeRemoved(int start, int count) { emptyState(); }
                });
                ((TextView) findViewById(R.id.librarySummary)).setText(getString(R.string.recording_summary, items.length(), CustomFunctions.fileSizeFormatter(totalBytes)));
                findViewById(R.id.libraryProgress).setVisibility(View.GONE);
                applyFilter();
            });
        });
    }
    private void scan(File directory, List<File> files) {
        File[] children = directory.listFiles();
        if (children == null) return;
        for (File file : children) {
            if (file.isDirectory() && Arrays.asList(RecordingCategory.VALUES).contains(file.getName())) scan(file, files);
            else if (file.isFile() && file.length() > 0 && (file.getName().endsWith(".m4a") || file.getName().endsWith(".amr"))
                    && !file.getAbsolutePath().equals(RecordingService.activeOutput())) files.add(file);
        }
    }
    private void applyFilter() { if (adapter != null) adapter.filterLibrary(category, search); }
    private void emptyState() {
        if (adapter != null) ((TextView) findViewById(R.id.librarySummary)).setText(getString(R.string.recording_summary,
                adapter.libraryCount(), CustomFunctions.fileSizeFormatter(adapter.libraryBytes())));
        boolean empty = adapter == null || adapter.getItemCount() == 0;
        findViewById(R.id.emptyLibrary).setVisibility(empty ? View.VISIBLE : View.GONE);
        ((TextView) findViewById(R.id.emptyTitle)).setText("all".equals(category) && search.isEmpty() ? R.string.empty_library : R.string.no_filter_results);
    }
    @Override public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        getMenuInflater().inflate(R.menu.action_bar_menu, menu);
        searchBtn = menu.findItem(R.id.menu_search_action);
        settingsBtn = menu.findItem(R.id.menu_settings_action);
        selectedItemsCountMenu = menu.findItem(R.id.menu_selected_items_count);
        SearchView searchView = (SearchView) searchBtn.getActionView();
        searchView.setQueryHint("Search recordings");
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String query) { return false; }
            @Override public boolean onQueryTextChange(String query) { search = query; applyFilter(); return true; }
        });
        return true;
    }
    @Override public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.menu_settings_action) { openSettings(); return true; }
        return super.onOptionsItemSelected(item);
    }
    private void openSettings() { startActivity(new Intent(this, SettingsActivity.class)); }
    @Override public void onRequestPermissionsResult(int request, @NonNull String[] permissions, @NonNull int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            Toast.makeText(this, "Microphone permission is required to record. You can grant it from Android App info.", Toast.LENGTH_LONG).show();
        updateStatus();
    }
    @Override protected void onSaveInstanceState(@NonNull Bundle out) {
        out.putBoolean("permissions_asked", permissionsAsked); out.putString("category", category);
        super.onSaveInstanceState(out);
    }
    @Override protected void onDestroy() {
        unregisterReceiver(changes); loader.shutdownNow();
        searchBtn = null; settingsBtn = null; selectedItemsCountMenu = null;
        super.onDestroy();
    }
}
