package zoro.benojir.callrecorder;

import android.app.Application;
import android.content.pm.ApplicationInfo;
import androidx.preference.PreferenceManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import zoro.benojir.callrecorder.activities.SettingsActivity;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class RecordingModeSettingsTest {
    private Application app;
    private ActivityController<SettingsActivity> controller;
    @Before public void setup() {
        app = RuntimeEnvironment.getApplication();
        org.robolectric.Shadows.shadowOf(app).denyPermissions("android.permission.CAPTURE_AUDIO_OUTPUT");
        PreferenceManager.getDefaultSharedPreferences(app).edit().clear().putBoolean("accessibility_recording", true).commit();
    }
    private void check(int flags, boolean visible) {
        app.getApplicationInfo().flags = (app.getApplicationInfo().flags
                & ~(ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) | flags;
        controller = Robolectric.buildActivity(SettingsActivity.class).setup().visible();
        controller.get().getSupportFragmentManager().executePendingTransactions();
        SettingsActivity.SettingsFragment fragment = (SettingsActivity.SettingsFragment)
                controller.get().getSupportFragmentManager().findFragmentById(R.id.settings);
        assertNotNull(fragment);
        assertEquals(visible, fragment.findPreference("accessibility_recording").isVisible());
        assertEquals(visible, fragment.findPreference("accessibility_settings").isVisible());
        if (!visible) assertFalse(PreferenceManager.getDefaultSharedPreferences(app).getBoolean("accessibility_recording", true));
    }
    @Test public void normalInstallShowsNonRootControls() { check(0, true); }
    @Test public void systemInstallHidesNonRootControlsEvenWhenPermissionIsMissing() { check(ApplicationInfo.FLAG_SYSTEM, false); }
    @Test public void updatedSystemInstallAlsoHidesNonRootControls() { check(ApplicationInfo.FLAG_UPDATED_SYSTEM_APP, false); }
    @Test public void protectedPermissionAlsoHidesNonRootControls() {
        org.robolectric.Shadows.shadowOf(app).grantPermissions("android.permission.CAPTURE_AUDIO_OUTPUT");
        check(0, false);
    }
    @After public void cleanup() { if (controller != null) controller.pause().stop().destroy(); }
}
