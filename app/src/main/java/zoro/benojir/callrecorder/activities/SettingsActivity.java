package zoro.benojir.callrecorder.activities;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreferenceCompat;
import com.google.android.material.appbar.MaterialToolbar;
import zoro.benojir.callrecorder.R;
import zoro.benojir.callrecorder.helpers.AppLock;
import zoro.benojir.callrecorder.helpers.RecorderHelper;

public class SettingsActivity extends LockedActivity {
    public static void applyTheme(android.content.Context context) {
        String theme = PreferenceManager.getDefaultSharedPreferences(context).getString("appearance", "device_default");
        AppCompatDelegate.setDefaultNightMode("dark_mode".equals(theme) ? AppCompatDelegate.MODE_NIGHT_YES
                : "light_mode".equals(theme) ? AppCompatDelegate.MODE_NIGHT_NO : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }
    @Override protected void onCreate(Bundle savedInstanceState) {
        applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);
        if (savedInstanceState == null) getSupportFragmentManager().beginTransaction().replace(R.id.settings, new SettingsFragment()).commit();
        MaterialToolbar toolbar = findViewById(R.id.toolbar_include);
        toolbar.setTitle("Settings");
        toolbar.setNavigationIcon(R.drawable.arrow_back_24);
        toolbar.setNavigationOnClickListener(v -> finish());
    }
    public static class SettingsFragment extends PreferenceFragmentCompat {
        @Override public void onCreatePreferences(Bundle state, String key) {
            setPreferencesFromResource(R.xml.root_preferences, key);
            findPreference("appearance").setOnPreferenceChangeListener((p, value) -> {
                PreferenceManager.getDefaultSharedPreferences(requireContext()).edit().putString("appearance", value.toString()).apply();
                applyTheme(requireContext());
                return true;
            });
            findPreference("audio_encoder").setOnPreferenceChangeListener((p, value) -> { audioRates("aac".equals(value)); return true; });
            audioRates("aac".equals(PreferenceManager.getDefaultSharedPreferences(requireContext()).getString("audio_encoder", "aac")));
            findPreference("accessibility_settings").setOnPreferenceClickListener(p -> {
                new AlertDialog.Builder(requireContext()).setTitle("Accessibility microphone support")
                        .setMessage(R.string.non_root_limitations).setPositiveButton("Open settings", (d, w) ->
                                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))).setNegativeButton("Cancel", null).show();
                return true;
            });
            findPreference("accessibility_recording").setOnPreferenceChangeListener((p, value) -> {
                if (Boolean.TRUE.equals(value)) new AlertDialog.Builder(requireContext()).setTitle("Non-root recording")
                        .setMessage(R.string.non_root_limitations).setPositiveButton("Enable", (d, w) ->
                                ((SwitchPreferenceCompat) p).setChecked(true)).setNegativeButton("Cancel", null).show();
                return !Boolean.TRUE.equals(value);
            });
            findPreference("notification_access").setOnPreferenceClickListener(p -> {
                new AlertDialog.Builder(requireContext()).setTitle("VoIP call detection").setMessage(R.string.voip_limitations)
                        .setPositiveButton("Open settings", (d, w) -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)))
                        .setNegativeButton("Cancel", null).show(); return true;
            });
            findPreference("permissions").setOnPreferenceClickListener(p -> {
                startActivity(new Intent(requireContext(), MainActivity.class).putExtra("request_permissions", true)); return true;
            });
            SwitchPreferenceCompat lock = findPreference("app_lock_ui");
            lock.setOnPreferenceChangeListener((p, value) -> {
                if (Boolean.TRUE.equals(value)) AppLock.prompt(requireActivity(), true, () -> { lock.setChecked(true); updateLock(); }, () -> { });
                else AppLock.prompt(requireActivity(), false, () -> { AppLock.disable(requireContext()); lock.setChecked(false); updateLock(); }, () -> { });
                return false;
            });
            findPreference("change_pin").setOnPreferenceClickListener(p -> {
                AppLock.prompt(requireActivity(), false, () -> AppLock.prompt(requireActivity(), true, () -> { }, () -> { }), () -> { }); return true;
            });
        }
        private void audioRates(boolean aac) { findPreference("sample_rate").setEnabled(aac); findPreference("bit_rate").setEnabled(aac); }
        private void updateLock() {
            boolean enabled = AppLock.enabled(requireContext());
            ((SwitchPreferenceCompat) findPreference("app_lock_ui")).setChecked(enabled);
            findPreference("change_pin").setEnabled(enabled);
            if (enabled) requireActivity().getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
            else requireActivity().getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        }
        @Override public void onResume() {
            super.onResume();
            updateLock();
            Preference status = findPreference("permissions");
            status.setSummary(RecorderHelper.hasCallAudioPermission(requireContext())
                    ? "Privileged call audio permission granted · Tap to review runtime permissions"
                    : "Microphone mode · No privileged call audio permission · Tap to grant permissions");
        }
    }
}
