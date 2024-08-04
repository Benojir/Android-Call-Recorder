package com.venomdino.callrecorder.helpers;

import android.annotation.SuppressLint;
import android.content.Context;
import android.media.MediaRecorder;
import android.util.Log;
import android.widget.Toast;

import com.venomdino.callrecorder.activities.MainActivity;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;

public class RecordingHelper {

    private static final String TAG = MainActivity.TAG;
    Context context;
    public static MediaRecorder recorder;
    private final String phoneNumber;
    private String finalFileName;

//__________________________________________________________________________________________________

    public RecordingHelper(Context context, String phoneNumber){
        this.context = context;
        this.phoneNumber = phoneNumber;
    }
//__________________________________________________________________________________________________

    @SuppressLint("SimpleDateFormat")
    public void startVoiceRecoding() {

            String directoryPath = context.getExternalFilesDir("/recordings/").getAbsolutePath() + "/";

            File dir = context.getExternalFilesDir("/recordings/");

            if (!dir.exists()) {
                if (!dir.mkdirs()){
                    Toast.makeText(context, "Failed to create directory.", Toast.LENGTH_SHORT).show();
                }
            }

            String fileName = ContactsHelper.getContactNameByPhoneNumber(phoneNumber, context)
                    + "_("
                    + phoneNumber
                    + ")_"
                    + new SimpleDateFormat("yyyy-MM-dd_hh-mm-ss").format(Calendar.getInstance().getTime())
                    + ".m4a";

            finalFileName = directoryPath + fileName;

            recorder = new MediaRecorder();
            recorder.reset();
            recorder.setAudioSource(MediaRecorder.AudioSource.VOICE_CALL);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            recorder.setAudioEncodingBitRate(16);
            recorder.setAudioSamplingRate(44100);
            recorder.setOutputFile(finalFileName);

            try {
                recorder.prepare();
                recorder.start();

                if (new SharedPreferencesHelper(context).isStartRecordingToastEnabled()){
                    Toast.makeText(context, "Recording started", Toast.LENGTH_SHORT).show();
                }
            }
            catch (Exception e) {
                Log.e(TAG, "startVoiceRecoding: ", e);
                Toast.makeText(context, "Recording start failed! " + e.getMessage(), Toast.LENGTH_SHORT).show();
                Log.d(TAG, "startVoiceRecoding: " + e.getMessage());
            }
    }
//--------------------------------------------------------------------------------------------------

    public void stopVoiceRecoding() {

        try {
            recorder.release();
            recorder.stop();
            recorder.reset();
            recorder = null;

            if (new SharedPreferencesHelper(context).isStopRecordingToastEnabled()){
                Toast.makeText(context, "Recording saved to: " + finalFileName, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "stopVoiceRecoding:", e);
            recorder = null;

            if (new SharedPreferencesHelper(context).isStopRecordingToastEnabled()){
                Toast.makeText(context, "Recording saved", Toast.LENGTH_SHORT).show();
            }
            Log.d(TAG, "stopVoiceRecoding: " + e.getMessage());
        }
    }
}
