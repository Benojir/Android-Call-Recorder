package zoro.benojir.callrecorder.helpers;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;

public final class ContactsHelper {
    private ContactsHelper() { }
    public static String getContactNameByPhoneNumber(Context context, String number) {
        if (number == null || number.isEmpty() || context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED)
            return "Unknown";
        Uri lookup = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));
        try (Cursor cursor = context.getContentResolver().query(lookup,
                new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0);
                if (name != null && !name.isEmpty()) return name;
            }
        } catch (RuntimeException ignored) { /* Contacts are optional and may be revoked mid-call. */ }
        return "Unknown";
    }
}
