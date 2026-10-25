package zoro.benojir.callrecorder.services;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.IBinder;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.preference.PreferenceManager;
import zoro.benojir.callrecorder.R;
import zoro.benojir.callrecorder.activities.MainActivity;
import zoro.benojir.callrecorder.helpers.RecorderHelper;
import zoro.benojir.callrecorder.helpers.RecordingCategory;
import zoro.benojir.callrecorder.helpers.RecordingDiagnostics;

/** All detection routes feed this service; only it owns microphone capture. */
public class RecordingService extends Service implements SharedPreferences.OnSharedPreferenceChangeListener {
    public static final String START = "recording.START", END = "recording.END";
    public static final String STOP = "recording.STOP", PAUSE = "recording.PAUSE";
    public static final String UI_STOP = "recording.UI_STOP", UI_PAUSE = "recording.UI_PAUSE";
    public static final String CHANGED = "zoro.benojir.callrecorder.RECORDINGS_CHANGED";
    static final int NOTIFICATION_ID = 1;
    static final int FINISHED_NOTIFICATION_ID = 3;
    static final long FINISHED_STATUS_MS = 1500;
    // Main-thread generation: a delayed cleanup must never remove a later call's notification.
    private static long notificationGeneration;
    private static volatile boolean running;
    private static volatile String activeId;
    private static volatile boolean paused;
    private static volatile String outputPath;
    private static RecordingService instance;
    private RecorderHelper recorder;
    private SharedPreferences preferences;
    private boolean foregroundStarted, finished;

    public static boolean isRunning() { return running; }
    public static String activeSession() { return activeId; }
    public static boolean isPaused() { return paused; }
    public static String activeOutput() { return outputPath; }
    @Override public void onCreate() {
        super.onCreate();
        running = false;
        instance = this;
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        preferences.registerOnSharedPreferenceChangeListener(this);
        RecordingSessions.channel(this);
    }
    @Nullable @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            if (recorder == null) finishService(); else refreshNotification();
            return START_NOT_STICKY;
        }
        String action = intent.getAction();
        String id = intent.getStringExtra("session");
        RecordingSessions.Session session = RecordingSessions.get(id);
        if (START.equals(action)) {
            // Rejected/stale starts stop immediately, without advertising microphone use.
            if (session == null || session.suppressed || !enabled(session)) {
                if (recorder == null) finishService(); else refreshNotification();
                return START_NOT_STICKY;
            }
            if (recorder != null) {
                if (!id.equals(activeId)) {
                    session.suppressed = true;
                    if ("manual".equals(session.number)) RecordingSessions.discard(id);
                    toast("Another recording is already active");
                }
                refreshNotification();
                return START_NOT_STICKY;
            }
            finished = false;
            notificationGeneration++;
            cancelNotification(this, FINISHED_NOTIFICATION_ID);
            try {
                startForeground(NOTIFICATION_ID, notification(session), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
                foregroundStarted = true;
            }
            catch (RuntimeException e) {
                finishService();
                RecordingSessions.offerStart(this, session);
                return START_NOT_STICKY;
            }
            getSystemService(NotificationManager.class).cancel(id, 2);
            RecorderHelper candidate = createRecorder();
            candidate.setOnErrorListener(() -> {
                if (recorder != candidate) return;
                session.suppressed = true;
                if ("manual".equals(session.number)) RecordingSessions.discard(session.id);
                finishService();
            });
            if (candidate.start(session.category, session.number)) {
                recorder = candidate;
                running = true;
                outputPath = recorder.getOutput().getAbsolutePath();
                activeId = id;
                paused = false;
                refreshNotification();
                if (preferences.getBoolean("start_toast", false)) toast("Recording started");
                changed();
            } else {
                session.suppressed = true; // No repeated microphone attempts during this call.
                if ("manual".equals(session.number)) RecordingSessions.discard(id);
                toast("Recording failed. Check microphone permission and choose a compatible audio source.");
                finishService();
            }
        } else if (id != null && id.equals(activeId)) {
            if (END.equals(action)) finishService();
            else if ((STOP.equals(action) && preferences.getBoolean("notification_stop", true)) || UI_STOP.equals(action)) {
                if (session != null) session.suppressed = true;
                if (session != null && "manual".equals(session.number)) RecordingSessions.discard(id);
                finishService();
            } else if ((PAUSE.equals(action) && preferences.getBoolean("notification_pause", true)) || UI_PAUSE.equals(action)) {
                if (recorder != null && recorder.togglePause()) {
                    paused = recorder.isPaused();
                    refreshNotification();
                    changed();
                } else toast("Pause/resume failed on this device");
            }
        } else if (recorder == null) finishService(STOP.equals(action) || PAUSE.equals(action)
                || UI_STOP.equals(action) || UI_PAUSE.equals(action));
        return START_NOT_STICKY;
    }

    protected RecorderHelper createRecorder() { return new RecorderHelper(this); }

    /** Call-state callbacks already run on main: do not round-trip through startService to stop. */
    public static void endSession(String id) {
        RecordingService live = instance;
        if (live == null || id == null) return;
        Runnable end = () -> { if (id.equals(activeId)) live.finishService(); };
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) end.run();
        else new android.os.Handler(android.os.Looper.getMainLooper()).post(end);
    }

    private boolean enabled(RecordingSessions.Session session) {
        if ("manual".equals(session.number)) return true;
        return preferences.getBoolean("is_call_recording_enabled", false)
                && ("phone".equals(session.category) || preferences.getBoolean("voip_enabled", false));
    }
    private Notification notification(RecordingSessions.Session session) {
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, RecordingSessions.CHANNEL)
                .setSmallIcon(R.drawable.keyboard_voice).setContentTitle(paused ? "Recording paused" : "Call Recorder")
                .setContentText(session == null ? "Preparing recorder" : RecordingCategory.label(session.category)
                        + " · " + (recorder == null ? "Preparing" : recorder.getSourceName()))
                .setOngoing(true).setOnlyAlertOnce(true).setContentIntent(open)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE);
        if (session != null && recorder != null) {
            if (preferences.getBoolean("notification_pause", true)) builder.addAction(R.drawable.pause_24,
                    paused ? "Resume" : "Pause", action(PAUSE, session.id));
            if (preferences.getBoolean("notification_stop", true)) builder.addAction(R.drawable.stop_circle_24,
                    "Stop", action(STOP, session.id));
        }
        return builder.build();
    }
    private PendingIntent action(String command, String id) {
        return PendingIntent.getService(this, id.hashCode(), RecordingSessions.command(this, command, id),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
    private void refreshNotification() {
        if (android.os.Build.VERSION.SDK_INT < 33 || checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                == android.content.pm.PackageManager.PERMISSION_GRANTED)
            getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, notification(RecordingSessions.get(activeId)));
    }
    private void finishService() {
        finishService(false);
    }
    private void finishService(boolean staleControl) {
        if (finished) {
            cancelNotification(this, NOTIFICATION_ID);
            stopSelf();
            return;
        }
        boolean announceEnd = staleControl || foregroundStarted || recorder != null;
        if (!announceEnd) {
            try { announceEnd = hasRecordingNotification(getSystemService(NotificationManager.class)); }
            catch (RuntimeException e) { android.util.Log.w("RecordingService", "Cannot query recording status", e); }
        }
        finished = true;
        long generation = ++notificationGeneration;
        running = false; // Detectors must not restart a service that is being torn down.
        String message;
        try { message = save(); }
        finally {
            try { clearForegroundStatus(); }
            finally { stopSelf(); }
        }
        if (announceEnd) {
            // A separate, non-FGS ID must actually reach notification listeners before removal.
            // Some ROM recording chips retain removed ongoing notifications until a saved update.
            try { getSystemService(NotificationManager.class).notify(FINISHED_NOTIFICATION_ID, endedNotification()); }
            catch (RuntimeException e) { android.util.Log.w("RecordingService", "Cannot publish ended status", e); }
        }
        // Saved UI is announced only after releasing capture and removing its ongoing status.
        RecordingDiagnostics.updateShutdown(this, running, NOTIFICATION_ID, 0);
        changed();
        if (message != null) toast(message);
        android.content.Context context = getApplicationContext();
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (generation != notificationGeneration || running) return;
            // NotificationManager/SystemUI handle removal asynchronously on some ROMs.
            cancelNotification(context, NOTIFICATION_ID);
            cancelNotification(context, FINISHED_NOTIFICATION_ID);
            RecordingDiagnostics.updateShutdown(context, false, NOTIFICATION_ID, FINISHED_STATUS_MS);
        }, FINISHED_STATUS_MS);
    }
    /** Remove FGS ownership before publishing an independent terminal status. */
    private void clearForegroundStatus() {
        foregroundStarted = false;
        try { stopForeground(STOP_FOREGROUND_REMOVE); }
        catch (RuntimeException e) { android.util.Log.w("RecordingService", "Cannot remove foreground status", e); }
        finally { cancelNotification(this, NOTIFICATION_ID); }
    }
    Notification endedNotification() {
        return new NotificationCompat.Builder(this, RecordingSessions.CHANNEL)
                .setSmallIcon(R.drawable.done_icon).setContentTitle("Recording stopped")
                .setCategory(NotificationCompat.CATEGORY_STATUS).setOngoing(false)
                .setUsesChronometer(false).setShowWhen(false).setWhen(0)
                .setTimeoutAfter(FINISHED_STATUS_MS).setAutoCancel(true)
                .setOnlyAlertOnce(true).setSilent(true).build();
    }
    private static boolean hasRecordingNotification(NotificationManager manager) {
        for (android.service.notification.StatusBarNotification status : manager.getActiveNotifications())
            if (status.getId() == NOTIFICATION_ID && status.getTag() == null) return true;
        return false;
    }
    private static void cancelNotification(android.content.Context context, int id) {
        try { context.getSystemService(NotificationManager.class).cancel(id); }
        catch (RuntimeException e) { android.util.Log.w("RecordingService", "Cannot cancel recording status", e); }
    }
    private String save() {
        RecorderHelper closing = recorder;
        recorder = null;
        activeId = null;
        outputPath = null;
        paused = false;
        if (closing != null) {
            boolean saved = false;
            try { saved = closing.stop(); }
            catch (RuntimeException e) { android.util.Log.e("RecordingService", "Recorder shutdown failed", e); }
            if (!saved || preferences.getBoolean("saved_toast", false)) return closing.getLastStopMessage();
        }
        return null;
    }
    private void changed() { sendBroadcast(new Intent(CHANGED).setPackage(getPackageName())); }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
    @Override public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        RecordingSessions.Session session = RecordingSessions.get(activeId);
        if (session != null && !enabled(session)) { session.suppressed = true; finishService(); }
        else if (recorder != null && ("notification_stop".equals(key) || "notification_pause".equals(key))) refreshNotification();
    }
    @Override public void onDestroy() {
        RecordingSessions.Session session = RecordingSessions.get(activeId);
        if (session != null) {
            session.suppressed = true;
            if ("manual".equals(session.number)) RecordingSessions.discard(session.id);
        }
        try { finishService(); }
        finally {
            if (instance == this) instance = null;
            preferences.unregisterOnSharedPreferenceChangeListener(this);
            super.onDestroy();
        }
    }
}
