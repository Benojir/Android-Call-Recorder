package zoro.benojir.callrecorder.services;

import android.Manifest;
import android.accessibilityservice.AccessibilityService;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.view.accessibility.AccessibilityEvent;
import androidx.preference.PreferenceManager;
import zoro.benojir.callrecorder.helpers.RecorderHelper;
import zoro.benojir.callrecorder.helpers.CustomFunctions;

/** User-enabled accessibility microphone sharing. No screen text, gestures or message content. */
@SuppressWarnings("deprecation")
public class CallAccessibilityService extends AccessibilityService implements SharedPreferences.OnSharedPreferenceChangeListener {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TelephonyManager telephony;
    private SharedPreferences preferences;
    private String session;
    private boolean offHook;
    private boolean listening;
    private final PhoneStateListener listener = new PhoneStateListener() {
        @Override public void onCallStateChanged(int state, String ignoredNumber) {
            if (state == TelephonyManager.CALL_STATE_OFFHOOK) offHook = true;
            else if (state == TelephonyManager.CALL_STATE_IDLE) offHook = false;
            // RINGING can describe a waiting second call; keep the current session.
            reconcile();
        }
    };
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            registerPhoneListener();
            reconcile();
            handler.postDelayed(this, 1500);
        }
    };
    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        if (CustomFunctions.isSystemApp(this) || RecorderHelper.hasCallAudioPermission(this)) {
            disableSelf();
            return;
        }
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        preferences.registerOnSharedPreferenceChangeListener(this);
        telephony = getSystemService(TelephonyManager.class);
        handler.removeCallbacks(poll);
        handler.post(poll);
    }
    private void registerPhoneListener() {
        if (listening || telephony == null || checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return;
        try { telephony.listen(listener, PhoneStateListener.LISTEN_CALL_STATE); listening = true; }
        catch (RuntimeException ignored) { }
    }
    private void reconcile() {
        if (preferences == null) return;
        boolean enabled = preferences.getBoolean("accessibility_recording", false)
                && preferences.getBoolean("is_call_recording_enabled", false)
                && !RecorderHelper.hasCallAudioPermission(this);
        AudioManager audio = getSystemService(AudioManager.class);
        if (enabled && offHook && session == null && audio.getMode() == AudioManager.MODE_IN_CALL)
            session = RecordingSessions.begin(this, "phone", "");
        if ((!enabled || !offHook) && session != null) {
            RecordingSessions.end(this, session);
            session = null;
        }
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    // This callback interrupts accessibility feedback, not the phone call.
    // Keep the session (including a user Stop) until the call actually ends.
    @Override public void onInterrupt() { }
    @Override public void onSharedPreferenceChanged(SharedPreferences prefs, String key) { reconcile(); }
    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (listening && telephony != null) telephony.listen(listener, PhoneStateListener.LISTEN_NONE);
        if (preferences != null) preferences.unregisterOnSharedPreferenceChangeListener(this);
        RecordingSessions.end(this, session);
        super.onDestroy();
    }
}
