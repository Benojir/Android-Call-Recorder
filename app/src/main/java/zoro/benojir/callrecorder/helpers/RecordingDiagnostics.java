package zoro.benojir.callrecorder.helpers;

import android.content.Context;
import android.app.AppOpsManager;
import android.app.NotificationManager;
import android.os.Build;
import android.os.Process;
import org.json.JSONObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Latest local result only. No phone numbers, contact names or audio paths. */
public final class RecordingDiagnostics {
    private RecordingDiagnostics() { }
    public static synchronized void save(Context context, JSONObject result) {
        try {
            Files.write(new File(context.getFilesDir(), "recording-result.json").toPath(),
                    result.toString(2).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) { android.util.Log.w("RecordingDiagnostics", "Cannot save result", e); }
    }
    public static synchronized JSONObject latest(Context context) {
        try {
            File file = new File(context.getFilesDir(), "recording-result.json");
            if (!file.isFile() || file.length() > 16384) return new JSONObject();
            return new JSONObject(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
        } catch (Exception e) { return new JSONObject(); }
    }

    /** Read only our own UID. Unknown/unsupported is never reported as microphone inactive. */
    public static Object microphoneActive(Context context) {
        if (Build.VERSION.SDK_INT < 30) return "unavailable_on_this_android_version";
        try {
            return context.getSystemService(AppOpsManager.class).isOpActive(
                    AppOpsManager.OPSTR_RECORD_AUDIO, Process.myUid(), context.getPackageName());
        } catch (RuntimeException e) { return "unavailable_" + e.getClass().getSimpleName(); }
    }

    public static synchronized void updateShutdown(Context context, boolean serviceActive, int notificationId, long delay) {
        try {
            JSONObject result = latest(context);
            Object notificationActive;
            try {
                notificationActive = false;
                for (android.service.notification.StatusBarNotification status
                        : context.getSystemService(NotificationManager.class).getActiveNotifications()) {
                    if (status.getId() == notificationId && status.getTag() == null) notificationActive = true;
                }
            } catch (RuntimeException e) { notificationActive = "unavailable_" + e.getClass().getSimpleName(); }
            result.put("recording_service_active", serviceActive)
                    .put("recording_notification_active", notificationActive)
                    .put("microphone_appop_active", microphoneActive(context))
                    .put("shutdown_check_delay_ms", delay);
            save(context, result);
        } catch (Exception e) { android.util.Log.w("RecordingDiagnostics", "Cannot describe shutdown", e); }
    }
}
