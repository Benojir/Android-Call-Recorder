package zoro.benojir.callrecorder.activities;

import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.preference.ListPreference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.google.android.material.appbar.MaterialToolbar;

import zoro.benojir.callrecorder.R;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);

        if (preferences.getString("appearance", "device_default").equals("dark_mode")) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else if (preferences.getString("appearance", "device_default").equals("light_mode")) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }
        setContentView(R.layout.settings_activity);
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings, new SettingsFragment())
                    .commit();
        }

        MaterialToolbar toolbar = findViewById(R.id.toolbar_include);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Settings");
        }
        toolbar.setNavigationIcon(AppCompatResources.getDrawable(this, R.drawable.arrow_back_24));
        toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);

            // Add a listener to restart the activity when the theme is changed
            ListPreference appearancePreference = findPreference("appearance");
            if (appearancePreference != null) {
                appearancePreference.setOnPreferenceChangeListener((preference, newValue) -> {
                    getActivity().recreate();
                    return true;
                });
            }
        }
    }
}