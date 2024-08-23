package zoro.benojir.callrecorder.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.telecom.Call;
import android.telecom.InCallService;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.preference.PreferenceManager;

import java.util.HashMap;
import java.util.Map;

import zoro.benojir.callrecorder.BuildConfig;
import zoro.benojir.callrecorder.R;
import zoro.benojir.callrecorder.helpers.RecorderHelper;

public class MyInCallService extends InCallService {

    public static final String CHANNEL_ID = BuildConfig.APPLICATION_ID;
    private final Map<Call, RecorderHelper> activeRecorders = new HashMap<>();
    private Context sContext;

    @Override
    public void onCallAdded(Call call) {
        super.onCallAdded(call);
        sContext = this;

        call.registerCallback(new Call.Callback() {
            @Override
            public void onStateChanged(Call call, int state) {
                super.onStateChanged(call, state);
                Log.d("MyInCallService", "Call state changed: " + state);

                if (state == Call.STATE_ACTIVE) {
                    startRecording(call);
                } else if (state == Call.STATE_DISCONNECTED || state == Call.STATE_DISCONNECTING) {
                    stopRecording(call);
                }
            }
        });
    }

    @Override
    public void onCallRemoved(Call call) {
        super.onCallRemoved(call);
        stopRecording(call);
    }

    private void startRecording(Call call) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(sContext);
        if (preferences.getBoolean("is_call_recording_enabled", false)) {
            createNotificationChannel();

            NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(sContext, CHANNEL_ID)
                    .setContentTitle("Call Recorder")
                    .setContentText("Call recording in progress")
                    .setSmallIcon(R.drawable.keyboard_voice)
                    .setOngoing(true);

            Notification notification = notificationBuilder.build();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
            } else {
                startForeground(1, notification);
            }

            RecorderHelper recorderHelper = new RecorderHelper(sContext, call.getDetails().getHandle().getSchemeSpecificPart());
            recorderHelper.startRecoding();
            activeRecorders.put(call, recorderHelper);
        }
    }

    private void stopRecording(Call call) {
        RecorderHelper recorderHelper = activeRecorders.remove(call);
        if (recorderHelper != null) {
            recorderHelper.stopVoiceRecoding();
        }

        if (activeRecorders.isEmpty()) {
            stopForeground(true);
        }
    }

    private void createNotificationChannel() {
        NotificationChannel serviceChannel = new NotificationChannel(
                CHANNEL_ID,
                "Call Recorder Service Channel",
                NotificationManager.IMPORTANCE_NONE
        );
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(serviceChannel);
    }
}
