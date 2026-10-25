package zoro.benojir.callrecorder.activities;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreferenceCompat;
import com.google.android.material.appbar.MaterialToolbar;
import zoro.benojir.callrecorder.R;
import zoro.benojir.callrecorder.helpers.AppLock;
import zoro.benojir.callrecorder.helpers.RecorderHelper;
import zoro.benojir.callrecorder.helpers.CustomFunctions;
import zoro.benojir.callrecorder.helpers.RecordingDiagnostics;

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
            for (String option : new String[]{"audio_encoder", "audio_source", "compatible_call_audio"}) {
                findPreference(option).setOnPreferenceChangeListener((p, value) -> {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(this::audioRates);
                    return true;
                });
            }
            findPreference("recording_result").setOnPreferenceClickListener(p -> {
                org.json.JSONObject result = RecordingDiagnostics.latest(requireContext());
                String details = result.toString();
                try { details = result.toString(2); } catch (org.json.JSONException ignored) { }
                final String copy = details;
                new AlertDialog.Builder(requireContext()).setTitle("Last recording result").setMessage(details)
                        .setPositiveButton("Copy details", (d,w) -> CustomFunctions.copyTextToClipboard(requireContext(), copy))
                        .setNegativeButton("Close", null).show();
                return true;
            });
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
        private void audioRates() {
            if (!isAdded()) return;
            android.content.SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
            boolean privileged = RecorderHelper.hasCallAudioPermission(requireContext());
            boolean automatic = "auto".equals(prefs.getString("audio_source", "auto"));
            boolean compatible = privileged && automatic && prefs.getBoolean("compatible_call_audio", true);
            findPreference("compatible_call_audio").setVisible(privileged);
            findPreference("compatible_call_audio").setEnabled(automatic);
            findPreference("audio_encoder").setEnabled(!compatible);
            boolean aac = !compatible && "aac".equals(prefs.getString("audio_encoder", "aac"));
            findPreference("sample_rate").setEnabled(aac); findPreference("bit_rate").setEnabled(aac);
        }
        private void updateRecordingMode() {
            boolean system = CustomFunctions.isSystemApp(requireContext());
            boolean privileged = RecorderHelper.hasCallAudioPermission(requireContext());
            boolean showAccessibility = !system && !privileged;
            findPreference("accessibility_recording").setVisible(showAccessibility);
            findPreference("accessibility_settings").setVisible(showAccessibility);
            if (!showAccessibility) ((SwitchPreferenceCompat) findPreference("accessibility_recording")).setChecked(false);
            findPreference("permissions").setSummary(privileged
                    ? "Privileged call audio permission granted · Tap to review runtime permissions"
                    : system ? "System app · Protected audio permission missing. Check the module and reboot."
                    : "Microphone mode · No privileged call audio permission · Tap to grant permissions");
        }
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
            updateRecordingMode();
            audioRates();
            findPreference("recording_result").setSummary(RecordingDiagnostics.latest(requireContext())
                    .optString("message", "No recording has completed yet"));
        }
    }
}
