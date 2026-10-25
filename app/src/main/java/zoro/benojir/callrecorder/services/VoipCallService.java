package zoro.benojir.callrecorder.services;

import android.app.Notification;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import androidx.preference.PreferenceManager;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import zoro.benojir.callrecorder.helpers.RecordingCategory;

/** Only ongoing call notifications from selected packages plus an active communication route. */
public class VoipCallService extends NotificationListenerService implements SharedPreferences.OnSharedPreferenceChangeListener {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, String> candidates = new HashMap<>();
    private final Map<String, String> sessions = new HashMap<>();
    private SharedPreferences preferences;
    private long quietSince;
    private final Runnable poll = new Runnable() {
        @Override public void run() { reconcile(); handler.postDelayed(this, 1500); }
    };
    @Override public void onListenerConnected() {
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        preferences.registerOnSharedPreferenceChangeListener(this);
        candidates.clear();
        StatusBarNotification[] notifications = getActiveNotifications();
        if (notifications != null) for (StatusBarNotification notification : notifications) remember(notification);
        handler.removeCallbacks(poll);
        handler.post(poll);
    }
    private void remember(StatusBarNotification sbn) {
        String category = RecordingCategory.forPackage(sbn.getPackageName());
        if (category == null) return;
        Notification notification = sbn.getNotification();
        boolean ongoing = (notification.flags & Notification.FLAG_ONGOING_EVENT) != 0;
        int callType = notification.extras.getInt("android.callType", -1);
        // CallStyle 1 = incoming, 2 = ongoing, 3 = screening. Never trigger on incoming alone.
        if (Notification.CATEGORY_CALL.equals(notification.category) && ongoing && (callType == -1 || callType == 2))
            candidates.put(sbn.getKey(), category);
        else candidates.remove(sbn.getKey());
    }
    @Override public void onNotificationPosted(StatusBarNotification sbn) { remember(sbn); reconcile(); }
    @Override public void onNotificationRemoved(StatusBarNotification sbn) { candidates.remove(sbn.getKey()); reconcile(); }
    private void reconcile() {
        if (preferences == null) return;
        boolean enabled = preferences.getBoolean("is_call_recording_enabled", false) && preferences.getBoolean("voip_enabled", false);
        boolean communicating = getSystemService(AudioManager.class).getMode() == AudioManager.MODE_IN_COMMUNICATION;
        if (communicating) quietSince = 0;
        else if (quietSince == 0) quietSince = android.os.SystemClock.elapsedRealtime();
        Iterator<Map.Entry<String, String>> iterator = sessions.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, String> entry = iterator.next();
            if (!enabled || !candidates.containsValue(entry.getKey())
                    || !preferences.getBoolean("voip_" + entry.getKey(), true)) {
                RecordingSessions.end(this, entry.getValue());
                iterator.remove();
            } else if (!communicating && android.os.SystemClock.elapsedRealtime() - quietSince >= 3000) {
                // Stop capturing after a persistent route loss, but retain suppression
                // until the ongoing call notification ends. Stop must not auto-restart.
                RecordingSessions.suppress(this, entry.getValue());
            }
        }
        if (enabled && communicating && sessions.isEmpty()) {
            for (String category : candidates.values()) {
                if (preferences.getBoolean("voip_" + category, true)) {
                    sessions.put(category, RecordingSessions.begin(this, category, ""));
                    break;
                }
            }
        }
    }
    @Override public void onSharedPreferenceChanged(SharedPreferences prefs, String key) { reconcile(); }
    @Override public void onListenerDisconnected() { cleanup(); }
    private void cleanup() {
        handler.removeCallbacksAndMessages(null);
        for (String id : sessions.values()) RecordingSessions.end(this, id);
        sessions.clear();
        candidates.clear();
        if (preferences != null) preferences.unregisterOnSharedPreferenceChangeListener(this);
        preferences = null;
    }
    @Override public void onDestroy() { cleanup(); super.onDestroy(); }
}
