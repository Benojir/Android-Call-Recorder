package zoro.benojir.callrecorder.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;

import zoro.benojir.callrecorder.BuildConfig;
import zoro.benojir.callrecorder.R;
import zoro.benojir.callrecorder.helpers.SharedPreferencesHelper;

import java.util.Objects;

public class SettingsActivity extends AppCompatActivity {

    CheckBox startToastCB, stopToastCB;
    SwitchCompat recordingOnOffSwitch;
    CardView appearanceCV, recordingSortingOrderCV;
    TextView appearanceValueTV, savedSortByNameTV;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        getSupportFragmentManager();
        Toolbar toolbar = findViewById(R.id.settings_toolbar);
        setSupportActionBar(toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        Objects.requireNonNull(getSupportActionBar()).setTitle("Settings");

        initVariables();

        Intent intent = getIntent();

        String activity_started_by;

        if (intent.hasExtra("activity_started_by")){
            activity_started_by = intent.getStringExtra("activity_started_by");
        }
        else{
            activity_started_by = "started by other app";
        }

        if (activity_started_by.equalsIgnoreCase(BuildConfig.APPLICATION_ID)){
            toolbar.setNavigationOnClickListener(view -> onBackPressed());
        }
        else{
            toolbar.setNavigationOnClickListener(view -> {
                Intent intent1 = new Intent(SettingsActivity.this, MainActivity.class);
                intent1.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent1);
                finish();
            });
        }

        SharedPreferencesHelper sharedPreferencesHelper = new SharedPreferencesHelper(SettingsActivity.this);
//..................................................................................................

        startToastCB.setChecked(sharedPreferencesHelper.isStartRecordingToastEnabled());
        stopToastCB.setChecked(sharedPreferencesHelper.isStopRecordingToastEnabled());
        recordingOnOffSwitch.setChecked(sharedPreferencesHelper.isCallRecordingEnabled());
        appearanceValueTV.setText(sharedPreferencesHelper.getAppearanceValue());
        savedSortByNameTV.setText(sharedPreferencesHelper.getRecordingSortOrder());
//..................................................................................................

        startToastCB.setOnCheckedChangeListener((compoundButton, isChecked) -> sharedPreferencesHelper.saveRecordingStartToastBoolean(isChecked));
        stopToastCB.setOnCheckedChangeListener((compoundButton, isChecked) -> sharedPreferencesHelper.saveRecordingStopToastBoolean(isChecked));

//,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,
        recordingOnOffSwitch.setOnCheckedChangeListener((compoundButton, isEnabled) -> sharedPreferencesHelper.saveCallRecordingEnabledOrNotBoolean(isEnabled));
//,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,

        appearanceCV.setOnClickListener(view -> {
            PopupMenu popupMenu = new PopupMenu(SettingsActivity.this, view);
            popupMenu.getMenuInflater().inflate(R.menu.appearance_value_popup_menu, popupMenu.getMenu());

            popupMenu.setOnMenuItemClickListener(menuItem -> {
                sharedPreferencesHelper.saveAppearanceValue(menuItem.getTitle().toString());
                appearanceValueTV.setText(menuItem.getTitle().toString());

                if (menuItem.getTitle().toString().equalsIgnoreCase(getString(R.string.dark_mode))){
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                }
                else if (menuItem.getTitle().toString().equalsIgnoreCase(getString(R.string.light_mode))){
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                }
                else{
                    Toast.makeText(SettingsActivity.this, "Restart the app to see effect.", Toast.LENGTH_LONG).show();
                }
                return true;
            });
            popupMenu.show();
        });


        recordingSortingOrderCV.setOnClickListener(view -> {
            PopupMenu popupMenu = new PopupMenu(SettingsActivity.this, view);
            popupMenu.getMenuInflater().inflate(R.menu.sort_by_popup_menu, popupMenu.getMenu());

            popupMenu.setOnMenuItemClickListener(menuItem -> {
                sharedPreferencesHelper.saveSortRecordingOrder(menuItem.getTitle().toString());
                savedSortByNameTV.setText(menuItem.getTitle().toString());
                Toast.makeText(SettingsActivity.this, "Restart the app to see effect.", Toast.LENGTH_LONG).show();
                return true;
            });
            popupMenu.show();
        });
//,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    }

    //__________________________________________________________________________________________________

    private void initVariables() {
        startToastCB = findViewById(R.id.startToastCB);
        stopToastCB = findViewById(R.id.stopToastCB);
        recordingOnOffSwitch = findViewById(R.id.recordingOnOffSwitch);

        recordingSortingOrderCV = findViewById(R.id.recording_sort_order_cardview);
        savedSortByNameTV = findViewById(R.id.savedSortByNameTV);

        appearanceCV = findViewById(R.id.appearance_cardview);
        appearanceValueTV = findViewById(R.id.appearance_value_tv);
    }
}