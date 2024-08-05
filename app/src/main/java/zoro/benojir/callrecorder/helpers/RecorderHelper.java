package zoro.benojir.callrecorder.helpers;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.MediaRecorder;
import android.util.Log;
import android.widget.Toast;

import androidx.preference.PreferenceManager;

import java.io.File;
import java.text.DateFormat;
import java.util.Calendar;

public class RecorderHelper {

    private static final String TAG = "MADARA";
    private final Context context;
    public static MediaRecorder recorder;
    private final String phoneNumber;
    private final SharedPreferences preferences;

//__________________________________________________________________________________________________

    public RecorderHelper(Context context, String phoneNumber) {
        this.context = context;
        this.phoneNumber = phoneNumber;
        preferences = PreferenceManager.getDefaultSharedPreferences(context);
    }
//__________________________________________________________________________________________________

    public void startRecoding() {
        File directory = context.getExternalFilesDir("/recordings/");

        if (!directory.exists()) {
            if (!directory.mkdirs()) {
                Toast.makeText(context, "Failed to create directory.", Toast.LENGTH_SHORT).show();
            }
        }


        String fileName = directory.getAbsolutePath() + "/" + getFileName();

        recorder = new MediaRecorder();
        recorder.reset();
        recorder.setAudioSource(MediaRecorder.AudioSource.VOICE_CALL);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        recorder.setAudioEncodingBitRate(16);
        recorder.setAudioSamplingRate(44100);
        recorder.setOutputFile(fileName);

        try {
            recorder.prepare();
            recorder.start();

            if (preferences.getBoolean("start_toast", false)) {
                Toast.makeText(context, "Recording started", Toast.LENGTH_SHORT).show();
            }

        } catch (Exception e) {
            Log.e(TAG, "startVoiceRecoding: ", e);
            Toast.makeText(context, "Recording start failed! " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
//--------------------------------------------------------------------------------------------------

    public void stopVoiceRecoding() {

        try {
            recorder.release();
            recorder.stop();
            recorder.reset();
            recorder = null;

            if (preferences.getBoolean("saved_toast", false)) {
                Toast.makeText(context, "Recording saved successfully!", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "stopVoiceRecoding:", e);
            recorder = null;

            if (preferences.getBoolean("saved_toast", false)) {
                Toast.makeText(context, "Recording saved!", Toast.LENGTH_SHORT).show();
            }
        }
    }

//    ----------------------------------------------------------------------------------------------

    private String getFileName() {
        return ContactsHelper.getContactNameByPhoneNumber(phoneNumber, context)
                + "_("
                + phoneNumber
                + ")_"
                + DateFormat.getDateTimeInstance().format(Calendar.getInstance().getTime())
                + ".m4a";
    }
}
