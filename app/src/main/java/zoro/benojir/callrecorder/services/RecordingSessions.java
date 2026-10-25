package zoro.benojir.callrecorder.services;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import zoro.benojir.callrecorder.R;

/** Main-thread session registry also invalidates delayed notification actions after a call ends. */
public final class RecordingSessions {
    public static final String CHANNEL = "recording_status";
    private static final Map<String, Session> sessions = new LinkedHashMap<>();
    private RecordingSessions() { }
    public static final class Session {
        public final String id = UUID.randomUUID().toString();
        public final String category;
        public final String number;
        public boolean suppressed;
        Session(String category, String number) { this.category = category; this.number = number; }
    }
    public static String begin(Context context, String category, String number) {
        Session session = new Session(category, number);
        sessions.put(session.id, session);
        Intent intent = command(context, RecordingService.START, session.id);
        try { ContextCompat.startForegroundService(context, intent); }
        catch (RuntimeException e) {
            Log.w("RecordingSessions", "User interaction needed to start microphone service", e);
            offerStart(context, session);
        }
        return session.id;
    }
    public static Session get(String id) { return sessions.get(id); }
    public static void end(Context context, String id) {
        if (id == null) return;
        sessions.remove(id);
        stopCapture(context, id);
    }
    public static void suppress(Context context, String id) {
        Session session = sessions.get(id);
        if (session == null || session.suppressed) return;
        session.suppressed = true;
        stopCapture(context, id);
    }
    private static void stopCapture(Context context, String id) {
        manager(context).cancel(id, 2);
        if (RecordingService.isRunning()) {
            try { context.startService(command(context, RecordingService.END, id)); }
            catch (RuntimeException e) {
                Log.w("RecordingSessions", "Recorder is shutting down", e);
                if (id.equals(RecordingService.activeSession())) context.stopService(new Intent(context, RecordingService.class));
            }
        }
    }
    public static void discard(String id) { sessions.remove(id); }
    public static Intent command(Context context, String action, String id) {
        return new Intent(context, RecordingService.class).setAction(action)
                .setData(Uri.parse("callrecorder://session/" + id)).putExtra("session", id);
    }
    public static void channel(Context context) {
        manager(context).createNotificationChannel(new NotificationChannel(CHANNEL,
                "Call recording", NotificationManager.IMPORTANCE_LOW));
    }
    private static NotificationManager manager(Context context) { return context.getSystemService(NotificationManager.class); }
    public static void offerStart(Context context, Session session) {
        if (!sessions.containsKey(session.id) || session.suppressed) return;
        channel(context);
        PendingIntent start = PendingIntent.getForegroundService(context, session.id.hashCode(),
                command(context, RecordingService.START, session.id), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        try {
            manager(context).notify(session.id, 2, new NotificationCompat.Builder(context, CHANNEL)
                    .setSmallIcon(R.drawable.keyboard_voice).setContentTitle("Call detected")
                    .setContentText("Tap to start recording. Android requires microphone access.")
                    .setContentIntent(start).setAutoCancel(true).build());
        } catch (SecurityException e) { Log.w("RecordingSessions", "Notification permission missing", e); }
    }
}
