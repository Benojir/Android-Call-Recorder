package zoro.benojir.callrecorder.services;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.telecom.Call;
import android.telecom.InCallService;

import androidx.core.app.NotificationCompat;
import androidx.preference.PreferenceManager;

import zoro.benojir.callrecorder.BuildConfig;
import zoro.benojir.callrecorder.R;
import zoro.benojir.callrecorder.helpers.RecorderHelper;

public class RecorderInCallService extends InCallService {

    public static final String CHANNEL_ID = BuildConfig.APPLICATION_ID;
    public RecorderHelper recorderHelper;

    private Context sContext;
    private String phoneNumber;

    @Override
    public void onCallAdded(Call call) {
        super.onCallAdded(call);

        sContext = this;
        phoneNumber = call.getDetails().getHandle().getSchemeSpecificPart();

        call.registerCallback(new Call.Callback() {

            @SuppressLint("UnspecifiedImmutableFlag")
            @Override
            public void onStateChanged(Call call, int state) {
                super.onStateChanged(call, state);

                if (state == Call.STATE_ACTIVE) {

                    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(sContext);

                    if (preferences.getBoolean("is_call_recording_enabled", false)) {
                        createNotificationChannel();

                        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(sContext, CHANNEL_ID);

                        notificationBuilder.setContentTitle("Call Recorder");
                        notificationBuilder.setContentText("Call recording in progress");
                        notificationBuilder.setSmallIcon(R.drawable.keyboard_voice);
                        notificationBuilder.setOngoing(true);


                        Notification notification = notificationBuilder.build();

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
                        } else {
                            startForeground(1, notification);
                        }

                        recorderHelper = new RecorderHelper(sContext, phoneNumber);
                        recorderHelper.startRecoding();
                    }
                }
            }
        });
    }

    @Override
    public void onCallRemoved(Call call) {
        super.onCallRemoved(call);

        if (recorderHelper != null) {
            recorderHelper.stopVoiceRecoding();
        }
    }

//    --------------------------------------------------------------------------------------

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
