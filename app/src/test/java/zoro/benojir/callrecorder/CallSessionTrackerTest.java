package zoro.benojir.callrecorder;

import org.junit.Test;
import zoro.benojir.callrecorder.helpers.CallSessionTracker;
import static org.junit.Assert.*;

public class CallSessionTrackerTest {
    @Test public void conferenceParentReplacesChildrenWithoutEndingSession() {
        CallSessionTracker<String> calls = new CallSessionTracker<>();
        calls.update("first", 4);
        calls.update("second", 1); // dialing
        calls.update("first", 3); // holding
        assertTrue(calls.hasLiveCall());
        assertFalse(calls.hasActiveCall());
        calls.update("conference", 4);
        calls.update("first", 7);
        calls.remove("first"); calls.remove("second");
        assertTrue(calls.hasActiveCall());
        assertTrue(calls.hasLiveCall());
        calls.update("conference", 10);
        assertFalse(calls.hasLiveCall());
    }
    @Test public void disconnectingOneMemberDoesNotEndOtherMember() {
        CallSessionTracker<String> calls = new CallSessionTracker<>();
        calls.update("one", 4); calls.update("two", 4);
        calls.update("one", 10); calls.remove("one");
        assertTrue(calls.hasLiveCall());
        calls.remove("two"); assertFalse(calls.hasLiveCall());
    }
    @Test public void repeatedActiveCallbacksDoNotCreateAdditionalMembers() {
        CallSessionTracker<String> calls = new CallSessionTracker<>();
        calls.update("one", 4); calls.update("one", 4); calls.remove("one");
        assertFalse(calls.hasLiveCall());
    }
    @Test public void alreadyActiveCallIsRecognised() {
        CallSessionTracker<String> calls = new CallSessionTracker<>();
        calls.update("already_active", 4); assertTrue(calls.hasActiveCall());
    }
}
