package com.venomdino.callrecorder.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.venomdino.callrecorder.services.RecorderInCallService;

public class ActionReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {

        Bundle intentExtras = intent.getExtras();

        if (intentExtras.containsKey("stopRecording")){
            context.stopService(new Intent(context, RecorderInCallService.class));
        }
    }
}
