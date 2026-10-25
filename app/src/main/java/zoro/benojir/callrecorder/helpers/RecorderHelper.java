package zoro.benojir.callrecorder.helpers;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.media.MediaMetadataRetriever;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;
import androidx.preference.PreferenceManager;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;
import org.json.JSONObject;

/** One owner, one recorder. Never share a static MediaRecorder between telecom Call objects. */
public class RecorderHelper {
    private final Context context;
    private final SharedPreferences preferences;
    private MediaRecorder recorder;
    private File output;
    private boolean paused;
    private String sourceName;
    private String profileName;
    private String nativeError = "";
    private long startedAt;
    private Runnable errorListener;
    private boolean resetSucceeded, releaseSucceeded;
    private String lastStopMessage = "Recording could not be saved. Check the audio source and Last recording result in Settings.";

    public void setOnErrorListener(Runnable listener) { errorListener = listener; }

    public RecorderHelper(Context context) {
        this.context = context.getApplicationContext();
        preferences = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public boolean start(String category, String number) {
        if (recorder != null) return true;
        File root = context.getExternalFilesDir("recordings");
        if (root == null) root = new File(context.getFilesDir(), "recordings");
        File directory = new File(root, RecordingCategory.safeCategory(category));
        if (!directory.isDirectory() && !directory.mkdirs()) return false;
        boolean privileged = hasCallAudioPermission(context);
        String selected = preferences.getString("audio_source", "auto");
        int[] sources = AudioSettings.sources(selected, privileged, "phone".equals(category));
        String person = "phone".equals(category) && number != null && !number.isEmpty() && !"manual".equals(number)
                ? ContactsHelper.getContactNameByPhoneNumber(context, number) + "_(" + number + ")" : category;
        String stem = person.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
        if (stem.length() > 100) stem = stem.substring(0, 100);
        stem += "_" + new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS", Locale.US).format(new Date())
                + "_" + UUID.randomUUID().toString().substring(0, 8);
        for (int source : sources) {
            boolean compatible = AudioSettings.useCompatibleCallAudio(selected, privileged, "phone".equals(category),
                    preferences.getBoolean("compatible_call_audio", true), source);
            String codec = compatible ? "amr_nb" : preferences.getString("audio_encoder", "aac");
            boolean rawAmr = !compatible && ("amr_nb".equals(codec) || "amr_wb".equals(codec));
            output = new File(directory, stem + (rawAmr ? ".amr" : ".m4a"));
            nativeError = "";
            MediaRecorder candidate = null;
            try {
                candidate = Build.VERSION.SDK_INT >= 31 ? new MediaRecorder(context) : new MediaRecorder();
                final MediaRecorder watched = candidate;
                candidate.setOnErrorListener((ignored, what, extra) -> {
                    nativeError = what + "/" + extra;
                    if (recorder == watched && errorListener != null) errorListener.run();
                });
                candidate.setAudioSource(source);
                if (Build.VERSION.SDK_INT >= 30 && !compatible) candidate.setPrivacySensitive(false);
                candidate.setOutputFormat(compatible ? MediaRecorder.OutputFormat.MPEG_4
                        : "amr_nb".equals(codec) ? MediaRecorder.OutputFormat.AMR_NB
                        : "amr_wb".equals(codec) ? MediaRecorder.OutputFormat.AMR_WB : MediaRecorder.OutputFormat.MPEG_4);
                candidate.setAudioEncoder("amr_nb".equals(codec) ? MediaRecorder.AudioEncoder.AMR_NB
                        : "amr_wb".equals(codec) ? MediaRecorder.AudioEncoder.AMR_WB : MediaRecorder.AudioEncoder.AAC);
                candidate.setAudioChannels(1);
                candidate.setAudioSamplingRate(AudioSettings.sampleRate(codec, preferences.getString("sample_rate", "44100")));
                candidate.setAudioEncodingBitRate(AudioSettings.bitRate(codec, preferences.getString("bit_rate", "128000")));
                candidate.setOutputFile(output.getAbsolutePath());
                candidate.prepare();
                candidate.start();
                recorder = candidate;
                startedAt = SystemClock.elapsedRealtime();
                profileName = compatible ? "Compatible AMR-NB / M4A (8 kHz, 12.2 kbps)" : codec;
                sourceName = AudioSettings.sourceName(source) + " · " + (compatible ? "Compatible AMR-NB" : codec.toUpperCase(Locale.ROOT));
                return true;
            } catch (IOException | RuntimeException e) {
                Log.w("Recorder", "Audio source unavailable: " + source, e);
                release(candidate);
                if (output.exists() && !output.delete()) Log.w("Recorder", "Cannot remove failed output");
            }
        }
        output = null;
        return false;
    }

    public boolean togglePause() {
        if (recorder == null) return false;
        try {
            if (paused) recorder.resume(); else recorder.pause();
            paused = !paused;
            return true;
        } catch (RuntimeException e) {
            Log.w("Recorder", "Pause/resume unavailable", e);
            return false;
        }
    }


    public boolean stop() {
        MediaRecorder closing = recorder;
        recorder = null;
        paused = false;
        if (closing == null) return false;
        boolean saved = false;
        boolean stopped = false;
        String stopError = "";
        try {
            closing.stop();
            stopped = true;
        } catch (RuntimeException e) {
            stopError = e.getClass().getSimpleName();
            Log.w("Recorder", "Recorder finalization failed", e);
        } finally {
            release(closing);
        }
        long bytes = output == null ? 0 : output.length();
        long duration = readDuration(output);
        // Some audio HALs close the input when a call ends. Their stop() may throw
        // even though a valid, already-finalized recording exists. Never delete it.
        saved = bytes > 0 && (duration > 0 || (stopped && duration < 0));
        if (saved) lastStopMessage = stopped ? "Recording saved" : "Recording recovered and saved after audio input ended";
        else {
            lastStopMessage = bytes == 0 ? "No audio data was captured. Check the audio source and Last recording result in Settings."
                    : "Recording could not be finalized. The incomplete file was kept; check Last recording result in Settings.";
            if (output != null && output.isFile()) {
                if (bytes == 0) output.delete();
                else if (!output.renameTo(new File(output.getAbsolutePath() + ".incomplete")))
                    Log.w("Recorder", "Cannot mark incomplete recording");
            }
        }
        try {
            JSONObject result = new JSONObject().put("message", lastStopMessage)
                    .put("source", sourceName).put("profile", profileName)
                    .put("elapsed_ms", Math.max(0, SystemClock.elapsedRealtime() - startedAt))
                    .put("audio_duration_ms", duration).put("bytes", bytes)
                    .put("stop_error", stopError).put("native_error", nativeError)
                    .put("recorder_reset_succeeded", resetSucceeded).put("recorder_release_succeeded", releaseSucceeded)
                    .put("app_version", zoro.benojir.callrecorder.BuildConfig.VERSION_NAME)
                    .put("android", Build.VERSION.RELEASE).put("sdk", Build.VERSION.SDK_INT)
                    .put("device", Build.MANUFACTURER + " " + Build.MODEL);
            RecordingDiagnostics.save(context, result);
        } catch (Exception e) { Log.w("Recorder", "Cannot describe recording result", e); }
        return saved;
    }

    private long readDuration(File file) {
        if (file == null || !file.isFile() || file.length() == 0) return -1;
        try (MediaMetadataRetriever metadata = new MediaMetadataRetriever()) {
            metadata.setDataSource(file.getAbsolutePath());
            String duration = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            return duration == null ? -1 : Long.parseLong(duration);
        } catch (Exception e) { return -1; }
    }

    public String getLastStopMessage() { return lastStopMessage; }

    private void release(MediaRecorder closing) {
        if (closing == null) return;
        resetSucceeded = releaseSucceeded = false;
        try { closing.reset(); resetSucceeded = true; }
        catch (RuntimeException e) { Log.w("Recorder", "Recorder reset failed", e); }
        finally {
            try { closing.release(); releaseSucceeded = true; }
            catch (RuntimeException e) { Log.w("Recorder", "Recorder release failed", e); }
        }
    }

    public boolean isPaused() { return paused; }
    public String getSourceName() { return sourceName; }
    public File getOutput() { return output; }
    public static boolean hasCallAudioPermission(Context context) {
        return context.checkSelfPermission("android.permission.CAPTURE_AUDIO_OUTPUT") == PackageManager.PERMISSION_GRANTED;
    }
}
