package zoro.benojir.callrecorder.helpers;

import java.util.HashMap;
import java.util.Map;

/** Conference parents and children are members of ONE audio session, including held calls. */
public final class CallSessionTracker<T> {
    private final Map<T, Integer> calls = new HashMap<>();
    public void update(T call, int state) { calls.put(call, state); }
    public void remove(T call) { calls.remove(call); }
    public boolean hasActiveCall() { return calls.containsValue(4); }
    public boolean hasLiveCall() {
        for (int state : calls.values()) if (state != 7 && state != 10) return true;
        return false;
    }
    public void clear() { calls.clear(); }
}
