package com.venomdino.callrecorder.helpers;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

public class ContactsHelper {

    public static String getContactNameByPhoneNumber(String phone_number, Context context){

        String contactName = "";

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED){

            if (phone_number.equals("")){
                contactName = "Unknown_";
            }
            else{
                Uri uri=Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,Uri.encode(phone_number));

                String[] projection = new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME};

                Cursor cursor = context.getContentResolver().query(uri,projection,null,null,null);

                if (cursor != null) {
                    if(cursor.moveToFirst()) {
                        contactName = cursor.getString(0);
                    }
                    cursor.close();
                }

                if (contactName.equals("")){
                    contactName = "Unknown";
                }
            }
        }
        else {
            Toast.makeText(context, "Please grant contacts permission", Toast.LENGTH_SHORT).show();
        }

        return contactName;
    }
}
