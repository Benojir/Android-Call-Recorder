package com.venomdino.callrecorder.helpers;

import android.content.Context;
import android.content.SharedPreferences;

import com.venomdino.callrecorder.R;

public class SharedPrefs {
    private static final String APP_PREFS_NAME = "CallRecorderPreference";
    private final SharedPreferences mPreference;
    private final SharedPreferences.Editor mPrefEditor;
    private final Context context;

    public SharedPrefs(Context context) {
        this.mPreference = context.getSharedPreferences(APP_PREFS_NAME, Context.MODE_PRIVATE);
        this.mPrefEditor = mPreference.edit();
        this.context = context;
    }

//__________________________________________________________________________________________________

    public void saveRecordingStartToastBoolean(boolean isOn){
        mPrefEditor.putBoolean("show_start_recording_toast", isOn);
        mPrefEditor.commit();
    }

    public void saveRecordingStopToastBoolean(boolean isOn){
        mPrefEditor.putBoolean("show_stop_recording_toast", isOn);
        mPrefEditor.commit();
    }

    public void saveRecordingStopNotificationButtonBoolean(boolean isHidden){
        mPrefEditor.putBoolean("hide_stop_recording_notification_button", isHidden);
        mPrefEditor.commit();
    }

    public void saveAppearanceValue(String appearanceValue){
        mPrefEditor.putString("appearance_value", appearanceValue);
        mPrefEditor.commit();
    }

    public void saveCallRecordingEnabledOrNotBoolean(boolean isOn){
        mPrefEditor.putBoolean("is_call_recording_enabled", isOn);
        mPrefEditor.commit();
    }

    public void saveSortRecordingOrder(String order){
        mPrefEditor.putString("recording_sorting_order", order);
        mPrefEditor.commit();
    }
//__________________________________________________________________________________________________

    public boolean isStartRecordingToastEnabled(){
        return  mPreference.getBoolean("show_start_recording_toast", true);
    }

    public boolean isStopRecordingToastEnabled(){
        return  mPreference.getBoolean("show_stop_recording_toast", true);
    }

    public boolean isStopRecordingNotificationButtonHidden(){
        return  mPreference.getBoolean("hide_stop_recording_notification_button", true);
    }

    public String getAppearanceValue(){
        return  mPreference.getString("appearance_value", context.getString(R.string.device_default));
    }

    public boolean isCallRecordingEnabled(){
        return  mPreference.getBoolean("is_call_recording_enabled", true);
    }

    public String getRecordingSortOrder(){
        return  mPreference.getString("recording_sorting_order", context.getString(R.string.sort_by_new));
    }
}
