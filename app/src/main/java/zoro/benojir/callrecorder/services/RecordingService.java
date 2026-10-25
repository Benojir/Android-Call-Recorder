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

/** All detection routes feed this service; only it owns microphone capture. */
public class RecordingService extends Service implements SharedPreferences.OnSharedPreferenceChangeListener {
    public static final String START = "recording.START", END = "recording.END";
    public static final String STOP = "recording.STOP", PAUSE = "recording.PAUSE";
    public static final String UI_STOP = "recording.UI_STOP", UI_PAUSE = "recording.UI_PAUSE";
    public static final String CHANGED = "zoro.benojir.callrecorder.RECORDINGS_CHANGED";
    private static volatile boolean running;
    private static volatile String activeId;
    private static volatile boolean paused;
    private static volatile String outputPath;
    private RecorderHelper recorder;
    private SharedPreferences preferences;

    public static boolean isRunning() { return running; }
    public static String activeSession() { return activeId; }
    public static boolean isPaused() { return paused; }
    public static String activeOutput() { return outputPath; }
    @Override public void onCreate() {
        super.onCreate();
        running = true;
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        preferences.registerOnSharedPreferenceChangeListener(this);
        RecordingSessions.channel(this);
    }
    @Nullable @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) { stopSelf(); return START_NOT_STICKY; }
        String action = intent.getAction();
        String id = intent.getStringExtra("session");
        RecordingSessions.Session session = RecordingSessions.get(id);
        if (START.equals(action)) {
            // Satisfy startForegroundService even for an expired or duplicate PendingIntent.
            try { startForeground(1, notification(session), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE); }
            catch (RuntimeException e) {
                if (session != null) RecordingSessions.offerStart(this, session);
                stopSelf();
                return START_NOT_STICKY;
            }
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
            getSystemService(NotificationManager.class).cancel(id, 2);
            RecorderHelper candidate = new RecorderHelper(this);
            if (candidate.start(session.category, session.number)) {
                recorder = candidate;
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
        } else if (recorder == null) stopSelf();
        return START_NOT_STICKY;
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
            getSystemService(NotificationManager.class).notify(1, notification(RecordingSessions.get(activeId)));
    }
    private void finishService() {
        save();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }
    private void save() {
        if (recorder != null) {
            boolean saved = recorder.stop();
            if (preferences.getBoolean("saved_toast", false)) toast(saved ? "Recording saved" : "Recording was too short to save");
            recorder = null;
        }
        activeId = null;
        outputPath = null;
        paused = false;
        changed();
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
        save();
        running = false;
        preferences.unregisterOnSharedPreferenceChangeListener(this);
        super.onDestroy();
    }
}
