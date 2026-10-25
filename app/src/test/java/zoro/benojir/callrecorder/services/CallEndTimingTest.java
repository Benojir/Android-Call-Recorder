package zoro.benojir.callrecorder.services;

import android.content.Context;
import android.media.AudioManager;
import android.os.Looper;
import android.telecom.Call;
import android.telecom.DisconnectCause;
import androidx.preference.PreferenceManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import java.time.Duration;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class CallEndTimingTest {
    private ServiceController<MyInCallService> controller;
    private MyInCallService service;
    private AudioManager audio;
    @Before public void setup() {
        controller = Robolectric.buildService(MyInCallService.class).create(); service = controller.get();
        PreferenceManager.getDefaultSharedPreferences(service).edit().putBoolean("is_call_recording_enabled", true).commit();
        audio = (AudioManager)service.getSystemService(Context.AUDIO_SERVICE);
        audio.setMode(AudioManager.MODE_IN_CALL);
    }
    private Call call(int state) throws Exception {
        java.lang.reflect.Constructor<?> constructor = Call.class.getDeclaredConstructor(
                Class.forName("android.telecom.Phone"), String.class, Class.forName("android.telecom.InCallAdapter"), String.class, int.class);
        constructor.setAccessible(true);
        Call call = (Call)constructor.newInstance(null, "test-call", null, service.getPackageName(), 34);
        ReflectionHelpers.setField(call, "mState", state);
        service.onCallAdded(call);
        return call;
    }
    private String id() { return ReflectionHelpers.getField(service, "session"); }
    @Test public void localHangupEndsImmediately() throws Exception {
        Call call = call(Call.STATE_ACTIVE); String id = id(); assertNotNull(RecordingSessions.get(id));
        service.update(call, Call.STATE_DISCONNECTING);
        assertNull(RecordingSessions.get(id)); assertNull(id());
    }
    @Test public void remoteHangupEndsBeforeAudioRouteCleanup() throws Exception {
        Call call = call(Call.STATE_ACTIVE); String id = id();
        Call.Details details = org.robolectric.shadow.api.Shadow.newInstanceOf(Call.Details.class);
        ReflectionHelpers.setField(details, "mDisconnectCause", new DisconnectCause(DisconnectCause.REMOTE));
        ReflectionHelpers.setField(call, "mDetails", details);
        service.update(call, Call.STATE_DISCONNECTED);
        assertNull(RecordingSessions.get(id));
    }
    @Test public void endingOneConferenceMemberKeepsOtherMemberRecording() throws Exception {
        Call first = call(Call.STATE_ACTIVE); call(Call.STATE_ACTIVE); String id = id();
        service.update(first, Call.STATE_DISCONNECTING);
        assertEquals(id, id()); assertNotNull(RecordingSessions.get(id));
    }
    @Test public void conferenceParentCanReplaceChildDuringShortReorderingWindow() throws Exception {
        Call child = call(Call.STATE_ACTIVE); String id = id();
        service.onCallRemoved(child);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200));
        call(Call.STATE_ACTIVE);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));
        assertEquals(id, id()); assertNotNull(RecordingSessions.get(id));
    }
    @Test public void repeatedCleanupDoesNotPostponeEndDeadline() throws Exception {
        Call call = call(Call.STATE_ACTIVE); String id = id();
        service.onCallRemoved(call);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200));
        service.onCallRemoved(call);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50));
        assertNull(RecordingSessions.get(id));
    }
    @Test public void waitingUnansweredCallDoesNotKeepRecordingAfterHangup() throws Exception {
        Call first = call(Call.STATE_ACTIVE); call(Call.STATE_RINGING); String id = id();
        service.update(first, Call.STATE_DISCONNECTING);
        assertNull(RecordingSessions.get(id));
    }
    @After public void cleanup() { controller.destroy(); }
}
