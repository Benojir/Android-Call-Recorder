package zoro.benojir.callrecorder.services;

import android.os.Handler;
import android.os.Looper;
import android.telecom.Call;
import android.telecom.InCallService;
import androidx.preference.PreferenceManager;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import zoro.benojir.callrecorder.helpers.CallSessionTracker;

public class MyInCallService extends InCallService {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final CallSessionTracker<Call> tracker = new CallSessionTracker<>();
    private final Map<Call, Call.Callback> callbacks = new HashMap<>();
    private String session;
    private String number = "";
    private final Runnable endSession = () -> {
        if (!tracker.hasLiveCall()) {
            RecordingSessions.end(this, session);
            session = null;
            number = "";
        }
    };

    @Override public void onCallAdded(Call call) {
        super.onCallAdded(call);
        Call.Callback callback = new Call.Callback() {
            @Override public void onStateChanged(Call changed, int state) { update(changed, state); }
            @Override public void onChildrenChanged(Call changed, List<Call> children) { update(changed, changed.getState()); }
            @Override public void onParentChanged(Call changed, Call parent) { update(changed, changed.getState()); }
        };
        callbacks.put(call, callback);
        call.registerCallback(callback, handler);
        update(call, call.getState()); // Calls can already be active when this service binds.
    }
    private void update(Call call, int state) {
        tracker.update(call, state);
        if (call.getDetails() != null && call.getDetails().getHandle() != null && number.isEmpty())
            number = call.getDetails().getHandle().getSchemeSpecificPart();
        reconcile();
    }
    private void reconcile() {
        handler.removeCallbacks(endSession);
        if (session == null && tracker.hasActiveCall()
                && PreferenceManager.getDefaultSharedPreferences(this).getBoolean("is_call_recording_enabled", false)) {
            session = RecordingSessions.begin(this, "phone", number);
        }
        // Conference replacement may briefly remove every child before the parent arrives.
        // Held, dialing and connecting members keep the same recorder alive.
        if (!tracker.hasLiveCall()) handler.postDelayed(endSession, 1800);
    }
    @Override public void onCallRemoved(Call call) {
        Call.Callback callback = callbacks.remove(call);
        if (callback != null) call.unregisterCallback(callback);
        tracker.remove(call);
        reconcile();
        super.onCallRemoved(call);
    }
    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        for (Map.Entry<Call, Call.Callback> entry : callbacks.entrySet()) entry.getKey().unregisterCallback(entry.getValue());
        callbacks.clear();
        tracker.clear();
        RecordingSessions.end(this, session);
        super.onDestroy();
    }
}
