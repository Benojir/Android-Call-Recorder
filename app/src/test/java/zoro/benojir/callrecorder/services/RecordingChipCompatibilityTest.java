package zoro.benojir.callrecorder.services;

import android.Manifest;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.os.Handler;
import android.os.Looper;
import androidx.preference.PreferenceManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowNotificationManager;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;

/**
 * Compatibility fixture for the public Infinity X/AxDynamicBar notification observer:
 * https://github.com/hsslfe/frameworks_base_infinityx/blob/16/packages/SystemUI/src/com/android/systemui/axdynamicbar/data/source/NotificationIslandManager.kt
 * It keeps a removed RECORDING chip until a non-ongoing notification marks it SAVED.
 * Deferred delivery also models updates canceled before a notification listener sees them.
 * This exercises that protocol; it does not run the ROM or certify its SystemUI.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, shadows = RecordingChipCompatibilityTest.DeferredNotifications.class)
public class RecordingChipCompatibilityTest {
    @Implements(NotificationManager.class)
    public static class DeferredNotifications extends ShadowNotificationManager {
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final Map<Integer, Runnable> pending = new HashMap<>();
        private final Map<Integer, Notification> delivered = new HashMap<>();
        String chipState;
        Integer chipKey;

        @Implementation @Override protected void notify(int id, Notification notification) {
            super.notify(id, notification);
            Runnable previous = pending.remove(id);
            if (previous != null) handler.removeCallbacks(previous);
            Runnable delivery = () -> {
                pending.remove(id);
                delivered.put(id, notification);
                boolean ongoing = (notification.flags & Notification.FLAG_ONGOING_EVENT) != 0;
                if (ongoing && notification.actions != null && notification.actions.length >= 2) {
                    chipState = "RECORDING";
                    chipKey = id;
                } else if (!ongoing && chipState != null) {
                    chipState = "SAVED";
                    chipKey = id;
                }
            };
            pending.put(id, delivery);
            handler.postDelayed(delivery, 50);
        }
        @Implementation @Override protected void cancel(int id) {
            super.cancel(id);
            Runnable delivery = pending.remove(id);
            if (delivery != null) handler.removeCallbacks(delivery);
            if (delivered.remove(id) != null && chipKey != null && chipKey == id) {
                chipKey = null;
                if ("SAVED".equals(chipState)) chipState = null;
            }
        }
    }
    private Application app;
    private NotificationManager manager;
    private DeferredNotifications observer;
    private ServiceController<RecordingServiceTest.TestService> controller;
    private RecordingServiceTest.TestService service;
    private String session;

    @Before public void setup() {
        app = RuntimeEnvironment.getApplication();
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS);
        PreferenceManager.getDefaultSharedPreferences(app).edit().clear()
                .putBoolean("is_call_recording_enabled", true).commit();
        controller = Robolectric.buildService(RecordingServiceTest.TestService.class).create();
        service = controller.get();
        manager = app.getSystemService(NotificationManager.class);
        observer = Shadow.extract(manager);
        session = RecordingSessions.begin(app, "phone", "123");
        service.onStartCommand(RecordingSessions.command(app, RecordingService.START, session), 0, 1);
        idle(100);
        assertEquals("RECORDING", observer.chipState);
    }
    private void idle(long ms) { Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms)); }

    @Test public void immediateSameIdUpdateAndRemovalReproducesRetainedChip() {
        manager.notify(RecordingService.NOTIFICATION_ID, service.endedNotification());
        manager.cancel(RecordingService.NOTIFICATION_ID);
        idle(100);
        assertNull(observer.getNotification(RecordingService.NOTIFICATION_ID));
        assertEquals("RECORDING", observer.chipState);
    }
    @Test public void independentCompletionReachesSavedThenRemovalClearsChip() {
        service.onStartCommand(RecordingSessions.command(app, RecordingService.STOP, session), 0, 2);
        controller.destroy(); controller = null;
        idle(100);
        assertEquals("SAVED", observer.chipState);
        assertEquals(Integer.valueOf(RecordingService.FINISHED_NOTIFICATION_ID), observer.chipKey);
        assertFalse(RecordingService.isRunning());
        idle(2000);
        assertNull(observer.chipState);
        assertNull(observer.getNotification(RecordingService.FINISHED_NOTIFICATION_ID));
    }
    @Test public void oldChipStopActionRepairsStateWithoutRestartingRecorder() {
        service.onStartCommand(RecordingSessions.command(app, RecordingService.STOP, session), 0, 2);
        idle(2000);
        // Seed an old chip with no corresponding active notification or capture.
        Notification old = new androidx.core.app.NotificationCompat.Builder(app, RecordingSessions.CHANNEL)
                .setSmallIcon(zoro.benojir.callrecorder.R.drawable.keyboard_voice).setOngoing(true)
                .addAction(0, "Pause", null).addAction(0, "Stop", null).build();
        manager.notify(RecordingService.NOTIFICATION_ID, old); idle(100);
        manager.cancel(RecordingService.NOTIFICATION_ID);
        assertEquals("RECORDING", observer.chipState);
        controller.destroy();
        controller = Robolectric.buildService(RecordingServiceTest.TestService.class).create(); service = controller.get();
        service.onStartCommand(RecordingSessions.command(app, RecordingService.STOP, session), 0, 3);
        idle(100);
        assertEquals("SAVED", observer.chipState);
        assertEquals(0, service.starts);
        idle(2000);
        assertNull(observer.chipState);
    }
    @After public void cleanup() {
        if (controller != null) controller.destroy();
        RecordingSessions.end(app, session);
    }
}
