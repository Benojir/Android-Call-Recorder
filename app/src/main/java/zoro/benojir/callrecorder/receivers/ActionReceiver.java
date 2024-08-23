package zoro.benojir.callrecorder.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import zoro.benojir.callrecorder.services.MyInCallService;

public class ActionReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {

        Bundle intentExtras = intent.getExtras();

        if (intentExtras.containsKey("stopRecording")){
            context.stopService(new Intent(context, MyInCallService.class));
        }
    }
}
