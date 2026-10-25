package zoro.benojir.callrecorder.services;

import android.Manifest;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
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
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.time.Duration;
import zoro.benojir.callrecorder.helpers.RecorderHelper;
import zoro.benojir.callrecorder.helpers.RecordingDiagnostics;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class RecordingServiceTest {
    public static class TestService extends RecordingService {
        FakeRecorder engine;
        int starts;
        @Override protected RecorderHelper createRecorder() { starts++; engine = new FakeRecorder(this); return engine; }
    }
    private static class FakeRecorder extends RecorderHelper {
        int stops;
        boolean paused, failStop;
        File file;
        FakeRecorder(android.content.Context context) { super(context); file = new File(context.getFilesDir(), "service-test.m4a"); }
        @Override public boolean start(String category, String number) { return true; }
        @Override public boolean stop() { stops++; if (failStop) throw new IllegalStateException("Native recorder failure"); return true; }
        @Override public boolean togglePause() { paused = !paused; return true; }
        @Override public boolean isPaused() { return paused; }
        @Override public File getOutput() { return file; }
        @Override public String getSourceName() { return "Microphone"; }
    }
    private Application app;
    private SharedPreferences preferences;
    private ServiceController<TestService> controller;
    private TestService service;
    private final List<String> sessions = new ArrayList<>();
    @Before public void setup() {
        app = RuntimeEnvironment.getApplication();
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS);
        preferences = PreferenceManager.getDefaultSharedPreferences(app);
        preferences.edit().clear().putBoolean("is_call_recording_enabled", true).commit();
        controller = Robolectric.buildService(TestService.class).create(); service = controller.get();
    }
    private String session() { String id = RecordingSessions.begin(app, "phone", "123"); sessions.add(id); return id; }
    private void send(String action, String id) { service.onStartCommand(RecordingSessions.command(app, action, id), 0, 1); }
    private void assertStopped() {
        assertNull(RecordingService.activeSession()); assertNull(RecordingService.activeOutput());
        assertFalse(RecordingService.isRunning());
        assertTrue(Shadows.shadowOf(service).isStoppedBySelf());
        assertTrue(Shadows.shadowOf(service).isForegroundStopped());
        assertNull(Shadows.shadowOf(service).getLastForegroundNotification());
        assertNull(Shadows.shadowOf(app.getSystemService(NotificationManager.class))
                .getNotification(RecordingService.NOTIFICATION_ID));
    }
    @Test public void stopRemovesMicrophoneForegroundServiceAndDoesNotRestartSession() {
        String id = session(); send(RecordingService.START, id);
        assertEquals(id, RecordingService.activeSession());
        send(RecordingService.UI_STOP, id);
        assertEquals(1, service.engine.stops); assertTrue(RecordingSessions.get(id).suppressed); assertStopped();
        send(RecordingService.START, id); assertEquals(1, service.starts); assertStopped();
    }
    @Test public void failedRecorderStopStillRemovesForegroundService() {
        String id = session(); send(RecordingService.START, id); service.engine.failStop = true;
        send(RecordingService.STOP, id); assertStopped();
    }
    @Test public void expiredStartNeverPromotesServiceToMicrophoneForeground() {
        String id = session(); RecordingSessions.end(app, id);
        send(RecordingService.START, id);
        assertEquals(0, service.starts);
        assertEquals(0, Shadows.shadowOf(service).getLastForegroundNotificationId());
        assertStopped();
    }
    @Test public void disablingNotificationStopIgnoresStaleButtonButUiCanStop() {
        String id = session(); send(RecordingService.START, id);
        preferences.edit().putBoolean("notification_stop", false).commit();
        send(RecordingService.STOP, id); assertEquals(id, RecordingService.activeSession()); assertEquals(0, service.engine.stops);
        send(RecordingService.UI_STOP, id); assertStopped();
    }
    @Test public void pauseResumeAndStopShareOneRecorder() {
        String id = session(); send(RecordingService.START, id);
        send(RecordingService.PAUSE, id); assertTrue(RecordingService.isPaused());
        send(RecordingService.PAUSE, id); assertFalse(RecordingService.isPaused());
        assertEquals(1, service.starts);
        send(RecordingService.STOP, id); assertStopped();
    }
    @Test public void endingRemovedSessionStillStopsItsRecorder() {
        String id = session(); send(RecordingService.START, id);
        RecordingSessions.end(app, id); send(RecordingService.END, id); assertStopped();
    }
    @Test public void endSignalClosesRecorderWithoutWaitingForAnotherServiceIntent() {
        String id = session(); send(RecordingService.START, id);
        RecordingSessions.end(app, id);
        assertEquals(1, service.engine.stops);
        assertStopped();
    }
    @Test public void recorderErrorImmediatelyClosesForegroundCapture() {
        String id = session(); send(RecordingService.START, id);
        Runnable callback = org.robolectric.util.ReflectionHelpers.getField(service.engine, "errorListener");
        assertNotNull(callback); callback.run();
        assertTrue(RecordingSessions.get(id).suppressed);
        assertStopped();
    }
    @Test public void destroyingServiceReleasesRecorderAndForegroundNotification() {
        String id = session(); send(RecordingService.START, id);
        controller.destroy(); controller = null;
        assertEquals(1, service.engine.stops); assertNull(RecordingService.activeSession());
        assertTrue(Shadows.shadowOf(service).isForegroundStopped()); assertFalse(RecordingService.isRunning());
        assertNull(Shadows.shadowOf(app.getSystemService(NotificationManager.class))
                .getNotification(RecordingService.NOTIFICATION_ID));
    }
    @Test public void staleStopClearsOrphanNotificationWithoutAnActiveRecorder() {
        NotificationManager manager = app.getSystemService(NotificationManager.class);
        manager.notify(RecordingService.NOTIFICATION_ID, service.endedNotification());
        send(RecordingService.STOP, "ended-session");
        assertEquals(0, service.starts); assertStopped();
    }
    @Test public void stalePauseAlsoClearsOrphanNotification() {
        app.getSystemService(NotificationManager.class).notify(RecordingService.NOTIFICATION_ID, service.endedNotification());
        send(RecordingService.PAUSE, "ended-session");
        assertStopped();
    }
    @Test public void nullRestartClearsOrphanStatusButDoesNotStopAnActiveCapture() {
        app.getSystemService(NotificationManager.class).notify(RecordingService.NOTIFICATION_ID, service.endedNotification());
        service.onStartCommand(null, 0, 1); assertStopped();
        String id = session(); send(RecordingService.START, id);
        service.onStartCommand(null, 0, 2);
        assertEquals(id, RecordingService.activeSession()); assertEquals(0, service.engine.stops);
    }
    @Test public void endedStatusHasNoOngoingFlagTimerOrActions() {
        Notification ended = service.endedNotification();
        assertEquals(0, ended.flags & Notification.FLAG_ONGOING_EVENT);
        assertTrue(ended.actions == null || ended.actions.length == 0);
        assertFalse(ended.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER));
        assertFalse(ended.extras.getBoolean(Notification.EXTRA_SHOW_WHEN));
        assertEquals(0, ended.when);
        assertEquals(Notification.CATEGORY_STATUS, ended.category);
    }
    @Test public void delayedCleanupRemovesLateStatusAndUpdatesDiagnostics() throws Exception {
        String id = session(); send(RecordingService.START, id); send(RecordingService.STOP, id);
        // Emulate a delayed enqueue that arrives after stopForeground's removal.
        app.getSystemService(NotificationManager.class).notify(RecordingService.NOTIFICATION_ID, service.endedNotification());
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));
        assertStopped();
        assertFalse(RecordingDiagnostics.latest(app).getBoolean("recording_notification_active"));
        assertFalse(RecordingDiagnostics.latest(app).getBoolean("recording_service_active"));
        assertEquals(RecordingService.FINISHED_STATUS_MS, RecordingDiagnostics.latest(app).getLong("shutdown_check_delay_ms"));
        assertNull(Shadows.shadowOf(app.getSystemService(NotificationManager.class))
                .getNotification(RecordingService.FINISHED_NOTIFICATION_ID));
    }
    @Test public void delayedCleanupAndOldActionsCannotRemoveNextCallsStatus() {
        String first = session(); send(RecordingService.START, first); send(RecordingService.STOP, first);
        String next = session(); send(RecordingService.START, next);
        send(RecordingService.STOP, first); send(RecordingService.PAUSE, first);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));
        assertEquals(next, RecordingService.activeSession());
        assertEquals(0, service.engine.stops); assertFalse(RecordingService.isPaused());
        assertNotNull(Shadows.shadowOf(app.getSystemService(NotificationManager.class))
                .getNotification(RecordingService.NOTIFICATION_ID));
    }
    @Test public void onlyCaptureServiceDeclaresMicrophoneForegroundType() throws Exception {
        ServiceInfo capture = app.getPackageManager().getServiceInfo(new ComponentName(app, RecordingService.class), 0);
        ServiceInfo observer = app.getPackageManager().getServiceInfo(new ComponentName(app, MyInCallService.class), 0);
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE, capture.getForegroundServiceType());
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE, observer.getForegroundServiceType());
    }
    @Test public void completionUsesIndependentNonForegroundIdAndSurvivesServiceDestruction() {
        String id = session(); send(RecordingService.START, id); send(RecordingService.STOP, id);
        NotificationManager manager = app.getSystemService(NotificationManager.class);
        controller.destroy(); controller = null;
        Notification completion = Shadows.shadowOf(manager).getNotification(RecordingService.FINISHED_NOTIFICATION_ID);
        assertNotNull(completion);
        assertEquals(0, completion.flags & (Notification.FLAG_ONGOING_EVENT | Notification.FLAG_FOREGROUND_SERVICE));
        assertEquals(RecordingService.FINISHED_STATUS_MS, completion.getTimeoutAfter());
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500));
        assertNotNull(Shadows.shadowOf(manager).getNotification(RecordingService.FINISHED_NOTIFICATION_ID));
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));
        assertNull(Shadows.shadowOf(manager).getNotification(RecordingService.FINISHED_NOTIFICATION_ID));
    }
    @Test public void nextCaptureRemovesPreviousCompletionAndKeepsItsOwnLaterCompletion() {
        String first = session(); send(RecordingService.START, first); send(RecordingService.STOP, first);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400));
        String next = session(); send(RecordingService.START, next);
        NotificationManager manager = app.getSystemService(NotificationManager.class);
        assertNull(Shadows.shadowOf(manager).getNotification(RecordingService.FINISHED_NOTIFICATION_ID));
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400));
        send(RecordingService.STOP, next);
        // The first call's cleanup deadline must not remove the second call's completion.
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(800));
        assertNotNull(Shadows.shadowOf(manager).getNotification(RecordingService.FINISHED_NOTIFICATION_ID));
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));
        assertNull(Shadows.shadowOf(manager).getNotification(RecordingService.FINISHED_NOTIFICATION_ID));
    }
    @Test public void staleControlRepairsRetainedChipEvenWhenSystemNotificationIsAlreadyGone() {
        send(RecordingService.STOP, "previous-process-session");
        assertNotNull(Shadows.shadowOf(app.getSystemService(NotificationManager.class))
                .getNotification(RecordingService.FINISHED_NOTIFICATION_ID));
        assertEquals(0, service.starts); assertStopped();
    }
    @Test public void expiredStartWithoutCaptureDoesNotPublishCompletion() {
        send(RecordingService.START, "expired-session");
        assertNull(Shadows.shadowOf(app.getSystemService(NotificationManager.class))
                .getNotification(RecordingService.FINISHED_NOTIFICATION_ID));
        assertStopped();
    }
    @After public void cleanup() {
        if (controller != null) controller.destroy();
        for (String id : sessions) RecordingSessions.end(app, id);
    }
}
