package zoro.benojir.callrecorder.activities;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.StrictMode;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.SearchView;
import androidx.core.app.ActivityCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.snackbar.Snackbar;

import zoro.benojir.callrecorder.BuildConfig;
import zoro.benojir.callrecorder.R;
import zoro.benojir.callrecorder.adapters.RecordingsListRVAdapter;
import zoro.benojir.callrecorder.databinding.ActivityMainBinding;
import zoro.benojir.callrecorder.helpers.CustomFunctions;
import zoro.benojir.callrecorder.helpers.SharedPreferencesHelper;

import org.apache.commons.io.FilenameUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private static final int REQUEST_PERMISSION_CODE = 4528;
    public static final String TAG = "MADARA";
    private boolean doubleBackPressed;
    public static MenuItem searchBtn, settingsBtn, selectedItemsCountMenu;
    private RecordingsListRVAdapter recyclerViewAdapter;
    private final JSONArray allFilesInformationJsonArray = new JSONArray();
    private final ArrayList<Integer> allPositions = new ArrayList<>();
    private final ArrayList<Uri> allFilesUriList = new ArrayList<>();
    private boolean isBottomScrollButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (new SharedPreferencesHelper(MainActivity.this).getAppearanceValue().equalsIgnoreCase(getString(R.string.dark_mode))) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else if (new SharedPreferencesHelper(MainActivity.this).getAppearanceValue().equalsIgnoreCase(getString(R.string.light_mode))) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

//--------------------------------------------------------------------------------------------------
        getSupportFragmentManager();
        MaterialToolbar toolbar = findViewById(R.id.toolbar_include);
        setSupportActionBar(toolbar);
        Objects.requireNonNull(getSupportActionBar()).setTitle(getResources().getString(R.string.app_name));
//--------------------------------------------------------------------------------------------------
        /* App is crashing when sharing multi files at once if I don't set this */
        StrictMode.VmPolicy.Builder smBuilder = new StrictMode.VmPolicy.Builder();
        StrictMode.setVmPolicy(smBuilder.build());
//--------------------------------------------------------------------------------------------------

        View headView = binding.navigationViewMenu.getHeaderView(0);

        if (CustomFunctions.isDarkModeOn(this)) {
            headView.setBackground(AppCompatResources.getDrawable(this, R.drawable.header_bg_night));
        }

        String versionString = getString(R.string.app_version) + BuildConfig.VERSION_NAME;
        ((TextView) headView.findViewById(R.id.header_layout_version_tv)).setText(versionString);

        Button updateBtn = headView.findViewById(R.id.updateBtnInHeaderLayout);

        CustomFunctions.checkForUpdateOnStartApp(this, updateBtn);

        updateBtn.setOnClickListener(view -> {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.github_release_page_link)));
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        });

//--------------------------------------------------------------------------------------------------

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, binding.getRoot(), toolbar, 0, 0);
        binding.getRoot().addDrawerListener(toggle);
        binding.getRoot().addDrawerListener(new DrawerLayout.DrawerListener() {
            @Override
            public void onDrawerSlide(@NonNull View drawerView, float slideOffset) {
                CustomFunctions.hideKeyboard(MainActivity.this, drawerView);
            }

            @Override
            public void onDrawerOpened(@NonNull View drawerView) {
                CustomFunctions.hideKeyboard(MainActivity.this, drawerView);
            }

            @Override
            public void onDrawerClosed(@NonNull View drawerView) {
                CustomFunctions.hideKeyboard(MainActivity.this, drawerView);
            }

            @Override
            public void onDrawerStateChanged(int newState) {

            }
        });
        toggle.syncState();

        navigationViewItemsClickedActions();

//        ------------------------------------------------------------------------------------------

        if (!CustomFunctions.isSystemApp(this)) {

            AlertDialog.Builder builder = new AlertDialog.Builder(this);

            builder.setTitle(getString(R.string.not_system_app_message_title));
            builder.setMessage(getString(R.string.not_system_app_message_body));
            builder.setIcon(R.drawable.error);
            builder.setCancelable(false);
            builder.setPositiveButton("OK", (dialog, which) -> {
                dialog.dismiss();
                finishAndRemoveTask();
            });
            builder.setNegativeButton("Read Post", (dialog, which) -> {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.tutorial_post_link))));
                    new Handler(Looper.getMainLooper()).postDelayed(this::finishAndRemoveTask, 2000);
                } catch (Exception e) {
                    Toast.makeText(this, "No app found to open this link", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    onBackPressed();
                }
            });
            builder.create();
            builder.show();

        } else {
            checkPermissions();

            File recordingFolderPath = getExternalFilesDir("/recordings/");

            if (!recordingFolderPath.exists()) {
                recordingFolderPath.mkdirs();
            }

            File[] recordedFiles = recordingFolderPath.listFiles();
            AtomicInteger increment = new AtomicInteger(1);

            if (recordedFiles != null && recordedFiles.length > 0) {

                binding.nothingFoundDesignContainer.setVisibility(View.GONE);
                binding.recyclerView.setVisibility(View.VISIBLE);
                binding.filesLoadingDesignContainer.setVisibility(View.VISIBLE);

                new Thread(() -> {

                    String sortOrder = new SharedPreferencesHelper(MainActivity.this).getRecordingSortOrder();

                    if (sortOrder.equalsIgnoreCase(getString(R.string.sort_by_name_ascending))) {
                        CustomFunctions.sortFilesByNameAscending(recordedFiles);
                    } else if (sortOrder.equalsIgnoreCase(getString(R.string.sort_by_name_descending))) {
                        CustomFunctions.sortFilesByNameDescending(recordedFiles);
                    } else if (sortOrder.equalsIgnoreCase(getString(R.string.sort_by_new))) {
                        CustomFunctions.sortNewestFilesFirst(recordedFiles);
                    } else if (sortOrder.equalsIgnoreCase(getString(R.string.sort_by_old))) {
                        CustomFunctions.sortOldestFilesFirst(recordedFiles);
                    } else {
                        CustomFunctions.sortNewestFilesFirst(recordedFiles);
                    }

                    for (File recordFile : recordedFiles) {

                        runOnUiThread(() -> {
                            String loadingFiles = "Loading files " + increment + "/" + recordedFiles.length;
                            binding.totalFilesLoadedTV.setText(loadingFiles);
                        });

                        increment.getAndIncrement();

                        boolean isValidRecordedFile = recordFile.isFile() && FilenameUtils.getExtension(recordFile.getAbsolutePath()).equalsIgnoreCase("m4a") && recordFile.length() > 0;

                        if (isValidRecordedFile) {

                            JSONObject fileInfo = new JSONObject();

                            try {
                                fileInfo.put("name", recordFile.getName());
                                fileInfo.put("size", CustomFunctions.fileSizeFormatter(recordFile.length()));
                                fileInfo.put("modified_date", CustomFunctions.timeFormatter(recordFile.lastModified()));
                                fileInfo.put("absolute_path", recordFile.getAbsolutePath());

                                allFilesInformationJsonArray.put(fileInfo);
                            } catch (Exception e) {
                                Log.e(TAG, "onCreate: ", e);
                            }
                        }
                    }

                    runOnUiThread(() -> {

                        if (allFilesInformationJsonArray.length() > 0) {

                            binding.filesLoadingDesignContainer.setVisibility(View.GONE);

                            recyclerViewAdapter = new RecordingsListRVAdapter(MainActivity.this, allFilesInformationJsonArray, allPositions, allFilesUriList);
                            binding.recyclerView.setAdapter(recyclerViewAdapter);

                            LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this);
                            linearLayoutManager.setOrientation(LinearLayoutManager.VERTICAL);

                            binding.recyclerView.setLayoutManager(linearLayoutManager);
                            binding.recyclerView.setItemAnimator(new DefaultItemAnimator());

                            if (searchBtn != null) {
                                searchBtn.setVisible(true);
                            }
                        } else {
                            binding.nothingFoundDesignContainer.setVisibility(View.VISIBLE);
                            binding.recyclerView.setVisibility(View.GONE);
                            binding.filesLoadingDesignContainer.setVisibility(View.GONE);
                        }
                    });
                }).start();


                if (isBottomScrollButton) {
                    binding.scrollButtonTopBottomFAB.setOnClickListener(view -> binding.recyclerView.scrollToPosition(binding.recyclerView.getAdapter().getItemCount() - 1));
                } else {
                    binding.scrollButtonTopBottomFAB.setOnClickListener(view -> binding.recyclerView.scrollToPosition(0));
                }

                binding.recyclerView.setOnScrollListener(new RecyclerView.OnScrollListener() {
                    @Override
                    public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {

                        if (newState == RecyclerView.SCROLL_STATE_IDLE) { // No scrolling
                            new Handler().postDelayed(() -> binding.scrollButtonTopBottomFAB.setVisibility(View.GONE), 2000); // delay of 2 seconds before hiding the fab
                        }
                    }

                    @Override
                    public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {

                        if (dy > 0) { // scrolling down
                            isBottomScrollButton = true;
                        } else if (dy < 0) { // scrolling up
                            isBottomScrollButton = false;
                        }
                        binding.scrollButtonTopBottomFAB.setVisibility(View.VISIBLE);
                    }
                });
            }
        }
    }

//    ----------------------------------------------------------------------------------------------

    private void navigationViewItemsClickedActions() {
        binding.navigationViewMenu.setNavigationItemSelectedListener(item -> {

            Intent intent;

            if (item.getItemId() == R.id.check_update_action) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.github_release_page_link))));
                return true;

            } else if (item.getItemId() == R.id.donate_me_action) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.donation_page_link))));
                return true;

            } else if (item.getItemId() == R.id.send_mail_action) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.app_issue_page_link))));
                return true;

            } else if (item.getItemId() == R.id.share_app_action) {

                intent = new Intent(Intent.ACTION_SEND);
                intent.setType("text/plain");
                intent.putExtra(Intent.EXTRA_TEXT, getResources().getString(R.string.app_sharing_message) + "\n" + getString(R.string.github_release_page_link));
                startActivity(Intent.createChooser(intent, "Share via"));
                return true;

            } else if (item.getItemId() == R.id.more_app_action) {

                intent = new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.more_apps_in_play_store)));
                startActivity(intent);
                return true;

            } else if (item.getItemId() == R.id.visitWeb_app_action) {

                intent = new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.website_link)));
                startActivity(intent);
                return true;

            } else if (item.getItemId() == R.id.visitGitHub_app_action) {
                intent = new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.github_profile_link)));
                startActivity(intent);
                return true;

            } else if (item.getItemId() == R.id.visitFb_app_action) {

                intent = new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.facebook_page_link)));
                startActivity(intent);
                return true;

            } else {
                return false;
            }
        });
    }

//__________________________________________________________________________________________________

    @Override
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {

        getMenuInflater().inflate(R.menu.menu_dropdown_right_corner, menu);

        searchBtn = menu.findItem(R.id.menu_search_action);
        settingsBtn = menu.findItem(R.id.menu_settings_action);
        selectedItemsCountMenu = menu.findItem(R.id.menu_selected_items_count);

        searchBtn.setVisible(false);

        SearchView searchView = (SearchView) searchBtn.getActionView();
        searchView.setMaxWidth(Integer.MAX_VALUE);
        searchView.setQueryHint("Search recordings...");
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                recyclerViewAdapter.getFilter().filter(newText);
                return false;
            }
        });

        searchView.setOnFocusChangeListener((view, b) -> {
            CustomFunctions.hideKeyboard(MainActivity.this, view);
            searchView.clearFocus();
        });

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {

        if (item.getItemId() == R.id.menu_settings_action) {

            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            intent.putExtra("activity_started_by", getPackageName());
            startActivity(intent);
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

//__________________________________________________________________________________________________

    private void checkPermissions() {

        boolean arePermissionsGranted = ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;

        if (!arePermissionsGranted) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.READ_CONTACTS
            }, REQUEST_PERMISSION_CODE);
        }
    }

//__________________________________________________________________________________________________

    @Override
    public void onBackPressed() {
        if (binding.getRoot().isDrawerOpen(GravityCompat.START)) {
            binding.getRoot().closeDrawer(GravityCompat.START);
        } else {
            if (doubleBackPressed) {
                super.onBackPressed();
                finish();
            } else {
                this.doubleBackPressed = true;
                Snackbar.make(binding.getRoot(), "Double back press to exit.", Snackbar.LENGTH_LONG).show();
                new Handler(Looper.getMainLooper()).postDelayed(() -> doubleBackPressed = false, 2000);
            }
        }
    }

//__________________________________________________________________________________________________

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

    }

    // _________________________________________________________________________________________________

    @SuppressLint("BatteryLife")
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode != REQUEST_PERMISSION_CODE || grantResults.length < 2
                || grantResults[0] != PackageManager.PERMISSION_GRANTED
                || grantResults[1] != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "Please allow all permission", Toast.LENGTH_SHORT).show();

            try {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
            } catch (Exception e) {
                CustomFunctions.simpleAlert(this, "Error", getString(R.string.app_info_page_opening_failed_message), "OK", AppCompatResources.getDrawable(this, R.drawable.error));
            }
        } else {

            Intent intent = new Intent();
            String packageName = getPackageName();
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);

            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + packageName));
                startActivity(intent);
            }
        }
    }
}