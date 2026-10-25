package zoro.benojir.callrecorder;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Process;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import zoro.benojir.callrecorder.helpers.RecordingDiagnostics;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class RecordingShutdownDiagnosticsTest {
    @Test public void microphoneSnapshotDistinguishesActualActivityFromPermission() {
        Context context = RuntimeEnvironment.getApplication();
        AppOpsManager ops = context.getSystemService(AppOpsManager.class);
        // Permission alone is not an active microphone operation.
        assertEquals(false, RecordingDiagnostics.microphoneActive(context));
        ops.startOpNoThrow(AppOpsManager.OPSTR_RECORD_AUDIO, Process.myUid(), context.getPackageName(), null, "test");
        assertEquals(true, RecordingDiagnostics.microphoneActive(context));
        ops.finishOp(AppOpsManager.OPSTR_RECORD_AUDIO, Process.myUid(), context.getPackageName(), null);
        assertEquals(false, RecordingDiagnostics.microphoneActive(context));
    }
    @Test public void unreadableMicrophoneStateIsReportedAsUnknownRatherThanInactive() {
        Context context = new ContextWrapper(RuntimeEnvironment.getApplication()) {
            @Override public Object getSystemService(String name) {
                if (APP_OPS_SERVICE.equals(name)) throw new SecurityException("Operation query denied");
                return super.getSystemService(name);
            }
        };
        assertEquals("unavailable_SecurityException", RecordingDiagnostics.microphoneActive(context));
    }
}
