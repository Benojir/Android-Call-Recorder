package zoro.benojir.callrecorder;
import org.junit.Test;
import zoro.benojir.callrecorder.helpers.AudioSettings;
import zoro.benojir.callrecorder.helpers.RecordingCategory;
import static org.junit.Assert.*;

public class AudioSettingsTest {
    @Test public void nonRootNeverAttemptsProtectedSources() {
        assertArrayEquals(new int[]{6, 1}, AudioSettings.sources("auto", false, true));
        for (String protectedSource : new String[]{"2", "3", "4"})
            assertEquals(0, AudioSettings.sources(protectedSource, false, true).length);
    }
    @Test public void automaticPrivilegedPhoneAndVoipUseDifferentSources() {
        assertArrayEquals(new int[]{4, 1}, AudioSettings.sources("auto", true, true));
        assertArrayEquals(new int[]{6, 1}, AudioSettings.sources("auto", true, false));
        assertArrayEquals(new int[]{4}, AudioSettings.sources("4", true, false));
    }
    @Test public void amrAlwaysUsesCodecCompatibleRates() {
        assertEquals(8000, AudioSettings.sampleRate("amr_nb", "48000"));
        assertEquals(12200, AudioSettings.bitRate("amr_nb", "256000"));
        assertEquals(16000, AudioSettings.sampleRate("amr_wb", "44100"));
        assertEquals(23850, AudioSettings.bitRate("amr_wb", "128000"));
    }
    @Test public void malformedPreferencesAreBounded() {
        assertEquals(44100, AudioSettings.sampleRate("aac", "bad"));
        assertEquals(32000, AudioSettings.bitRate("aac", "16"));
        assertEquals(48000, AudioSettings.sampleRate("aac", "192000"));
        assertArrayEquals(new int[]{1}, AudioSettings.sources("999", true, true));
    }
    @Test public void onlySupportedPackagesAreCategorised() {
        assertEquals("whatsapp", RecordingCategory.forPackage("com.whatsapp.w4b"));
        assertEquals("telegram", RecordingCategory.forPackage("org.thunderdog.challegram"));
        assertNull(RecordingCategory.forPackage("com.example.messaging"));
        assertEquals("manual", RecordingCategory.safeCategory("../../outside"));
    }
}
