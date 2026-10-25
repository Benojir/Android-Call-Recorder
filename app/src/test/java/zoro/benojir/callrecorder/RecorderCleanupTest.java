package zoro.benojir.callrecorder;

import android.media.MediaRecorder;
import android.media.MediaMetadataRetriever;
import org.robolectric.shadows.ShadowMediaMetadataRetriever;
import zoro.benojir.callrecorder.helpers.RecordingDiagnostics;
import java.io.File;
import java.nio.file.Files;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import zoro.benojir.callrecorder.helpers.RecorderHelper;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class RecorderCleanupTest {
    @Test public void automaticSystemCallConfiguresOriginalEncoderAndContainer() {
        android.app.Application context = RuntimeEnvironment.getApplication();
        org.robolectric.Shadows.shadowOf(context).grantPermissions("android.permission.CAPTURE_AUDIO_OUTPUT");
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString("audio_source", "auto").putBoolean("compatible_call_audio", true).commit();
        RecorderHelper helper = new RecorderHelper(context);
        try {
            assertTrue(helper.start("phone", ""));
            MediaRecorder recorder = ReflectionHelpers.getField(helper, "recorder");
            org.robolectric.shadows.ShadowMediaRecorder actual = org.robolectric.Shadows.shadowOf(recorder);
            assertEquals(MediaRecorder.AudioSource.VOICE_CALL, actual.getAudioSource());
            assertEquals(MediaRecorder.AudioEncoder.AMR_NB, actual.getAudioEncoder());
            assertEquals(MediaRecorder.OutputFormat.MPEG_4, actual.getOutputFormat());
            assertEquals(8000, actual.getAudioSamplingRate()); assertEquals(12200, actual.getAudioEncodingBitRate());
        } finally { helper.stop(); }
    }
    @Test public void customEncoderSettingsRemainAvailableWhenCompatibilityIsDisabled() {
        android.app.Application context = RuntimeEnvironment.getApplication();
        org.robolectric.Shadows.shadowOf(context).grantPermissions("android.permission.CAPTURE_AUDIO_OUTPUT");
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString("audio_source", "auto").putBoolean("compatible_call_audio", false)
                .putString("audio_encoder", "aac").putString("sample_rate", "16000").putString("bit_rate", "64000").commit();
        RecorderHelper helper = new RecorderHelper(context);
        try {
            assertTrue(helper.start("phone", ""));
            MediaRecorder recorder = ReflectionHelpers.getField(helper, "recorder");
            org.robolectric.shadows.ShadowMediaRecorder actual = org.robolectric.Shadows.shadowOf(recorder);
            assertEquals(MediaRecorder.AudioEncoder.AAC, actual.getAudioEncoder());
            assertEquals(16000, actual.getAudioSamplingRate()); assertEquals(64000, actual.getAudioEncodingBitRate());
        } finally { helper.stop(); }
    }
    private static class FailingRecorder extends MediaRecorder {
        int stops, resets, releases;
        boolean failReset, failRelease;
        @Override public void stop() { stops++; throw new IllegalStateException("short recording"); }
        @Override public void reset() { resets++; if (failReset) throw new IllegalStateException("reset failed"); }
        @Override public void release() { releases++; if (failRelease) throw new IllegalStateException("release failed"); }
    }
    @Test public void stopFailureStillResetsAndReleasesMicrophone() throws Exception {
        RecorderHelper helper = new RecorderHelper(RuntimeEnvironment.getApplication());
        FailingRecorder recorder = new FailingRecorder();
        ReflectionHelpers.setField(helper, "recorder", recorder);
        assertFalse(helper.stop());
        assertEquals(1, recorder.stops); assertEquals(1, recorder.resets); assertEquals(1, recorder.releases);
        assertNull(ReflectionHelpers.getField(helper, "recorder"));
        assertTrue(RecordingDiagnostics.latest(RuntimeEnvironment.getApplication()).getBoolean("recorder_reset_succeeded"));
        assertTrue(RecordingDiagnostics.latest(RuntimeEnvironment.getApplication()).getBoolean("recorder_release_succeeded"));
    }
    @Test public void resetAndReleaseFailuresCannotLeaveHelperMarkedRecording() throws Exception {
        RecorderHelper helper = new RecorderHelper(RuntimeEnvironment.getApplication());
        FailingRecorder recorder = new FailingRecorder(); recorder.failReset = true; recorder.failRelease = true;
        ReflectionHelpers.setField(helper, "recorder", recorder); ReflectionHelpers.setField(helper, "paused", true);
        assertFalse(helper.stop()); assertFalse(helper.stop());
        assertEquals(1, recorder.releases); assertFalse(helper.isPaused());
        assertFalse(RecordingDiagnostics.latest(RuntimeEnvironment.getApplication()).getBoolean("recorder_reset_succeeded"));
        assertFalse(RecordingDiagnostics.latest(RuntimeEnvironment.getApplication()).getBoolean("recorder_release_succeeded"));
    }
    @Test public void validLongRecordingIsRecoveredEvenWhenNativeStopThrows() throws Exception {
        android.content.Context context = RuntimeEnvironment.getApplication();
        RecorderHelper helper = new RecorderHelper(context);
        File output = new File(context.getFilesDir(), "long-call.m4a");
        Files.write(output.toPath(), new byte[]{1,2,3,4});
        ShadowMediaMetadataRetriever.addMetadata(output.getAbsolutePath(), MediaMetadataRetriever.METADATA_KEY_DURATION, "120000");
        ReflectionHelpers.setField(helper, "recorder", new FailingRecorder());
        ReflectionHelpers.setField(helper, "output", output);
        assertTrue(helper.stop()); assertTrue(output.exists());
        assertTrue(helper.getLastStopMessage().contains("recovered"));
        assertEquals(120000, RecordingDiagnostics.latest(context).getLong("audio_duration_ms"));
    }
    @Test public void unreadableNonemptyFileIsKeptInsteadOfDeletedOrCalledTooShort() throws Exception {
        android.content.Context context = RuntimeEnvironment.getApplication();
        RecorderHelper helper = new RecorderHelper(context);
        File output = new File(context.getFilesDir(), "interrupted-call.m4a");
        Files.write(output.toPath(), new byte[]{1,2,3,4});
        ReflectionHelpers.setField(helper, "recorder", new FailingRecorder());
        ReflectionHelpers.setField(helper, "output", output);
        assertFalse(helper.stop());
        File incomplete = new File(output.getAbsolutePath()+".incomplete");
        assertTrue(incomplete.exists()); assertEquals(4, incomplete.length());
        assertFalse(helper.getLastStopMessage().contains("short"));
    }
    @Test public void emptyCaptureHasAccurateFailureMessageAndLocalDiagnostics() throws Exception {
        android.content.Context context = RuntimeEnvironment.getApplication();
        RecorderHelper helper = new RecorderHelper(context);
        File output = new File(context.getFilesDir(), "empty-call.m4a");
        assertTrue(output.createNewFile());
        ReflectionHelpers.setField(helper, "recorder", new FailingRecorder());
        ReflectionHelpers.setField(helper, "output", output);
        assertFalse(helper.stop()); assertFalse(output.exists());
        assertTrue(helper.getLastStopMessage().contains("No audio data"));
        assertEquals("IllegalStateException", RecordingDiagnostics.latest(context).getString("stop_error"));
        assertFalse(RecordingDiagnostics.latest(context).toString().contains("empty-call"));
    }
}
