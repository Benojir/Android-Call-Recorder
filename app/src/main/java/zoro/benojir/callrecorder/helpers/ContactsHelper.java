package zoro.benojir.callrecorder.helpers;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.telephony.PhoneNumberUtils;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

public class ContactsHelper {

    public static String getContactNameByPhoneNumber(Context context, String phoneNumber) {
        String contactName = "";

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {

            Uri uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
            String[] projection = new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER};

            Cursor cursor = context.getContentResolver().query(uri, projection, null, null, null);

            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String storedNumber = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER));
                    if (PhoneNumberUtils.compare(storedNumber, phoneNumber)) {
                        contactName = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
                        break;
                    }
                }
                cursor.close();
            }
        } else {
            Toast.makeText(context, "Please grant contacts permission", Toast.LENGTH_SHORT).show();
        }

        if (contactName.isEmpty()){
            contactName = "Unknown";
        }

        return contactName;
    }
}
