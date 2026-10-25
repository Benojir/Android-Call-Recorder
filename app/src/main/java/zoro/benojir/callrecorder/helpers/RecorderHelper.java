package zoro.benojir.callrecorder.helpers;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.os.Build;
import android.util.Log;
import androidx.preference.PreferenceManager;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

/** One owner, one recorder. Never share a static MediaRecorder between telecom Call objects. */
public final class RecorderHelper {
    private final Context context;
    private final SharedPreferences preferences;
    private MediaRecorder recorder;
    private File output;
    private boolean paused;
    private String sourceName;

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
        String codec = preferences.getString("audio_encoder", "aac");
        boolean amr = "amr_nb".equals(codec) || "amr_wb".equals(codec);
        output = new File(directory, stem + (amr ? ".amr" : ".m4a"));
        for (int source : sources) {
            MediaRecorder candidate = null;
            try {
                candidate = Build.VERSION.SDK_INT >= 31 ? new MediaRecorder(context) : new MediaRecorder();
                candidate.setAudioSource(source);
                if (Build.VERSION.SDK_INT >= 30) candidate.setPrivacySensitive(false);
                candidate.setOutputFormat("amr_nb".equals(codec) ? MediaRecorder.OutputFormat.AMR_NB
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
                sourceName = AudioSettings.sourceName(source);
                return true;
            } catch (IOException | RuntimeException e) {
                Log.w("Recorder", "Audio source unavailable: " + source, e);
                if (candidate != null) candidate.release();
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
        if (recorder == null) return false;
        boolean saved = false;
        try {
            recorder.stop();
            saved = output != null && output.length() > 0;
        } catch (RuntimeException e) {
            Log.w("Recorder", "Recording too short or interrupted", e);
        } finally {
            recorder.release();
            recorder = null;
            paused = false;
            if (!saved && output != null && output.exists()) output.delete();
        }
        return saved;
    }

    public boolean isPaused() { return paused; }
    public String getSourceName() { return sourceName; }
    public File getOutput() { return output; }
    public static boolean hasCallAudioPermission(Context context) {
        return context.checkSelfPermission("android.permission.CAPTURE_AUDIO_OUTPUT") == PackageManager.PERMISSION_GRANTED;
    }
}
