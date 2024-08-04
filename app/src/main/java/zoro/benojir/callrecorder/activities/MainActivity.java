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
import android.widget.LinearLayout;
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
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;
import zoro.benojir.callrecorder.BuildConfig;
import zoro.benojir.callrecorder.R;
import zoro.benojir.callrecorder.adapters.RecordingsListRVAdapter;
import zoro.benojir.callrecorder.helpers.CustomFunctions;
import zoro.benojir.callrecorder.helpers.SharedPreferencesHelper;

import org.apache.commons.io.FilenameUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSION_CODE = 4528;
    public static final String TAG = "MADARA";
    private boolean doubleBackPressed = false;
    @SuppressLint("StaticFieldLeak")
    public static LinearLayout emptyFileIconContainer, fileLoadingInfoContainer;
    public static RecyclerView allFilesRecyclerView;
    public static MenuItem searchBtn, settingsBtn, menu_selected_items_count;
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private RecordingsListRVAdapter recordingsListRVAdapter;
    private FloatingActionButton scrollBackToTopBtn, scrollToBottomBtn;
    private TextView totalFileLoadedTv;
    private final JSONArray allFilesInformationJsonArray = new JSONArray();
    private final ArrayList<Integer> allPositions = new ArrayList<>();
    private final ArrayList<Uri> allFilesUriList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

//--------------------------------------------------------------------------------------------------

        if (new SharedPreferencesHelper(MainActivity.this).getAppearanceValue().equalsIgnoreCase(getString(R.string.dark_mode))) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else if (new SharedPreferencesHelper(MainActivity.this).getAppearanceValue().equalsIgnoreCase(getString(R.string.light_mode))) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }
//--------------------------------------------------------------------------------------------------

        initVariables();

        getSupportFragmentManager();
        Toolbar toolbar = findViewById(R.id.main_toolbar);
        setSupportActionBar(toolbar);
        Objects.requireNonNull(getSupportActionBar()).setTitle(getResources().getString(R.string.app_name));

        /* App is crashing when sharing multi files at once if I don't set this */
        StrictMode.VmPolicy.Builder smBuilder = new StrictMode.VmPolicy.Builder();
        StrictMode.setVmPolicy(smBuilder.build());
//--------------------------------------------------------------------------------------------------

        View headView = navigationView.getHeaderView(0);

        if (CustomFunctions.isDarkModeOn(this)) {
            headView.setBackground(AppCompatResources.getDrawable(this, R.drawable.header_bg2));
        }

        String versionString = getString(R.string.app_version) + BuildConfig.VERSION_NAME;
        ((TextView) headView.findViewById(R.id.header_layout_version_tv)).setText(versionString);

        Button updateBtnInHeaderLayout = headView.findViewById(R.id.updateBtnInHeaderLayout);

        CustomFunctions.checkForUpdateOnStartApp(this, updateBtnInHeaderLayout);

        updateBtnInHeaderLayout.setOnClickListener(view -> {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.github_release_page_link)));
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        });

//--------------------------------------------------------------------------------------------------

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar, 0, 0);
        drawerLayout.addDrawerListener(toggle);
        drawerLayout.addDrawerListener(new DrawerLayout.DrawerListener() {
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

        navigationView.setNavigationItemSelectedListener(item -> {
            Intent intent1;

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

                intent1 = new Intent(Intent.ACTION_SEND);
                intent1.setType("text/plain");
                intent1.putExtra(Intent.EXTRA_TEXT, getResources().getString(R.string.app_sharing_message) + "\n" + getString(R.string.github_release_page_link));
                startActivity(Intent.createChooser(intent1, "Share via"));
                return true;

            } else if (item.getItemId() == R.id.more_app_action) {

                intent1 = new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.more_apps_in_play_store)));
                startActivity(intent1);
                return true;

            } else if (item.getItemId() == R.id.visitWeb_app_action) {

                intent1 = new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.website_link)));
                startActivity(intent1);
                return true;

            } else if (item.getItemId() == R.id.visitGitHub_app_action) {
                intent1 = new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.github_profile_link)));
                startActivity(intent1);
                return true;

            } else if (item.getItemId() == R.id.visitFb_app_action) {

                intent1 = new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.facebook_page_link)));
                startActivity(intent1);
                return true;

            } else {
                return false;
            }
        });

//        ------------------------------------------------------------------------------------------
//
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

            if (!isPermissionGranted()) {
                askPermission();
                Toast.makeText(this, "Please allow all permissions.", Toast.LENGTH_SHORT).show();
            } else {

                File recordingFolderPath = getExternalFilesDir("/recordings/");

                if (!recordingFolderPath.exists()) {
                    recordingFolderPath.mkdirs();
                }

                File[] recordedFiles = recordingFolderPath.listFiles();

                Log.d(TAG, recordedFiles.length + "");

                if (recordedFiles != null && recordedFiles.length > 0) {

                    emptyFileIconContainer.setVisibility(View.GONE);
                    allFilesRecyclerView.setVisibility(View.VISIBLE);
                    fileLoadingInfoContainer.setVisibility(View.VISIBLE);

                    Handler handler = new Handler();

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

                        int i = 1, j = 0;

                        for (File recordFile : recordedFiles) {

                            int finalI = i;

                            handler.post(() -> totalFileLoadedTv.setText("Loading files " + finalI + "/" + recordedFiles.length));

                            i++;

                            if (recordFile.isFile() && FilenameUtils.getExtension(recordFile.getAbsolutePath()).equalsIgnoreCase("m4a") && recordFile.length() > 0) {


                                JSONObject fileInfo = new JSONObject();

                                try {
                                    fileInfo.put("name", recordFile.getName());
                                    fileInfo.put("size", CustomFunctions.fileSizeFormatter(recordFile.length()));
                                    fileInfo.put("modified_date", CustomFunctions.timeFormatter(recordFile.lastModified()));
                                    fileInfo.put("absolute_path", recordFile.getAbsolutePath());

                                    allFilesInformationJsonArray.put(fileInfo);
                                    allPositions.add(j);
                                    j++;
                                    allFilesUriList.add(Uri.fromFile(new File(recordFile.getAbsolutePath())));
                                } catch (Exception e) {
                                    Log.e(TAG, "onCreate: ", e);
                                }
                            }
                        }

                        handler.post(() -> {

                            if (allFilesInformationJsonArray.length() > 0) {

                                if (searchBtn != null)
                                    searchBtn.setVisible(true);

                                fileLoadingInfoContainer.setVisibility(View.GONE);

                                recordingsListRVAdapter = new RecordingsListRVAdapter(MainActivity.this, allFilesInformationJsonArray, allPositions, allFilesUriList);
                                allFilesRecyclerView.setAdapter(recordingsListRVAdapter);

                                LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getApplicationContext());
                                linearLayoutManager.setOrientation(LinearLayoutManager.VERTICAL);

                                allFilesRecyclerView.setLayoutManager(linearLayoutManager);
                                allFilesRecyclerView.setItemAnimator(new DefaultItemAnimator());
                            } else {
                                emptyFileIconContainer.setVisibility(View.VISIBLE);
                                allFilesRecyclerView.setVisibility(View.GONE);
                                fileLoadingInfoContainer.setVisibility(View.GONE);
                            }
                        });

                    }).start();


//                  33333333333333333333333333333333333333333333333333333333333333333333333333333333

                    scrollBackToTopBtn.setOnClickListener(view -> allFilesRecyclerView.scrollToPosition(0));
                    scrollToBottomBtn.setOnClickListener(view -> allFilesRecyclerView.scrollToPosition(allFilesRecyclerView.getAdapter().getItemCount() - 1));

                    allFilesRecyclerView.setOnScrollListener(new RecyclerView.OnScrollListener() {
                        @Override
                        public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {

                            if (newState == RecyclerView.SCROLL_STATE_IDLE) { // No scrolling
                                new Handler().postDelayed(() -> scrollBackToTopBtn.setVisibility(View.GONE), 2000); // delay of 2 seconds before hiding the fab
                                new Handler().postDelayed(() -> scrollToBottomBtn.setVisibility(View.GONE), 2000); // delay of 2 seconds before hiding the fab
                            }
                        }

                        @Override
                        public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {

                            if (dy > 0) { // scrolling down
                                scrollBackToTopBtn.setVisibility(View.GONE);
                                scrollToBottomBtn.setVisibility(View.VISIBLE);
                            } else if (dy < 0) { // scrolling up
                                scrollBackToTopBtn.setVisibility(View.VISIBLE);
                                scrollToBottomBtn.setVisibility(View.GONE);
                            }
                        }
                    });
                }
            }

        }
//        ------------------------------------------------------------------------------------------
    }

//__________________________________________________________________________________________________

    @Override
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {

        getMenuInflater().inflate(R.menu.menu_dropdown_right_corner, menu);

        searchBtn = menu.findItem(R.id.menu_search_action);
        settingsBtn = menu.findItem(R.id.menu_settings_action);
        menu_selected_items_count = menu.findItem(R.id.menu_selected_items_count);

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
                recordingsListRVAdapter.getFilter().filter(newText);
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
    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            if (doubleBackPressed) {
                super.onBackPressed();
                finish();
            } else {
                this.doubleBackPressed = true;
                Snackbar.make(drawerLayout, "Double back press to exit.", Snackbar.LENGTH_LONG).show();
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
                CustomFunctions.simpleAlert(this, "Error", getString(R.string.app_info_page_opening_failed_message), "Ok", AppCompatResources.getDrawable(this, R.drawable.error));
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

//__________________________________________________________________________________________________

    private boolean isPermissionGranted() {

        return ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;
    }

    private void askPermission() {
        ActivityCompat.requestPermissions(this, new String[]{
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.READ_CONTACTS
        }, REQUEST_PERMISSION_CODE);
    }


    private void initVariables() {
        emptyFileIconContainer = findViewById(R.id.empty_file_icon_container);
        fileLoadingInfoContainer = findViewById(R.id.file_loading_info_container);
        drawerLayout = findViewById(R.id.drawerLayout);
        navigationView = findViewById(R.id.navigation_drawer);
        allFilesRecyclerView = findViewById(R.id.allFilesRecyclerView);
        scrollBackToTopBtn = findViewById(R.id.scrollBackToTopBtn);
        scrollToBottomBtn = findViewById(R.id.scrollToBottomBtn);
        totalFileLoadedTv = findViewById(R.id.total_file_loaded_tv);
    }
}