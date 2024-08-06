package zoro.benojir.callrecorder.helpers;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import zoro.benojir.callrecorder.BuildConfig;
import zoro.benojir.callrecorder.R;

import org.apache.commons.io.comparator.LastModifiedFileComparator;
import org.apache.commons.io.comparator.NameFileComparator;
import org.json.JSONObject;
import org.jsoup.Jsoup;

import java.io.File;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class CustomFunctions {

    private static final String TAG = "MADARA";

    //__________________________________________________________________________________________________
    public static void hideKeyboard(Context context, View view) {
        InputMethodManager inputMethodManager = (InputMethodManager) context.getSystemService(Activity.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }
//__________________________________________________________________________________________________

    public static boolean isDarkModeOn(Context context) {
        int nightModeFlags = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return nightModeFlags == Configuration.UI_MODE_NIGHT_YES;
    }
//__________________________________________________________________________________________________

    public static String formatDuration(long durationMs) {
        long hours = TimeUnit.MILLISECONDS.toHours(durationMs);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60;

        if (hours > 0) {
            return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
        }
    }

//__________________________________________________________________________________________________

    public static boolean isSystemApp(Context context) {

        boolean isSystemApp = false;

        PackageManager pm = context.getPackageManager();
        List<ApplicationInfo> installedApps = pm.getInstalledApplications(0);

        for (ApplicationInfo ai : installedApps) {

            if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {

                if (ai.packageName.equalsIgnoreCase(context.getPackageName())) {
                    isSystemApp = true;
                }
            }
        }

        return isSystemApp;
    }
//__________________________________________________________________________________________________

    public static void sortOldestFilesFirst(File[] files) {
        new Thread(() -> Arrays.sort(files, LastModifiedFileComparator.LASTMODIFIED_COMPARATOR)).start();
//        Arrays.sort(files, LastModifiedFileComparator.LASTMODIFIED_COMPARATOR);
    }

    public static void sortNewestFilesFirst(File[] files) {
        Arrays.sort(files, LastModifiedFileComparator.LASTMODIFIED_REVERSE);
//        new Thread(() -> Arrays.sort(files, LastModifiedFileComparator.LASTMODIFIED_REVERSE)).start();
    }

    public static void sortFilesByNameAscending(File[] files) {
        Arrays.sort(files, NameFileComparator.NAME_COMPARATOR);
    }

    public static void sortFilesByNameDescending(File[] files) {
        Arrays.sort(files, NameFileComparator.NAME_REVERSE);
    }

    //__________________________________________________________________________________________________
    public static String fileSizeFormatter(long size) {
        if (size <= 0) return "0 Bytes";
        final String[] units = new String[]{"Bytes", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return new DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }
//__________________________________________________________________________________________________

    public static String timeFormatter(Long time) {
        @SuppressLint("SimpleDateFormat")
        DateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy - hh:mm:ss");
        return dateFormat.format(time);
    }
//__________________________________________________________________________________________________

    public static double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();

        DecimalFormat deciFormat = new DecimalFormat();
        deciFormat.setMaximumFractionDigits(places);
        return Double.parseDouble(deciFormat.format(value));
    }
//__________________________________________________________________________________________________

    public static String timeFormatterFromSeconds(int seconds) {

        int hoursInt = 0, minutesInt = 0, secondsInt;
        String hoursStr = "00", minutesStr = "00", secondsStr;

        if (seconds < 60) {
            secondsInt = seconds;
            secondsStr = String.valueOf(seconds);
        } else {
            secondsInt = seconds % 60;
            secondsStr = secondsInt + "";
            minutesInt = (seconds - secondsInt) / 60;

            if (minutesInt > 60) {

                hoursInt = (minutesInt - (minutesInt % 60)) / 60;
                minutesInt = minutesInt % 60;

                hoursStr = String.valueOf(hoursInt);
                minutesStr = String.valueOf(minutesInt);
            }
        }

        if (secondsInt < 10) {
            secondsStr = "0" + secondsStr;
        }
        if (minutesInt < 10) {
            if (minutesInt > 0) {
                minutesStr = "0" + minutesInt;
            }
        }
        if (hoursInt < 10) {
            if (hoursInt > 0) {
                hoursStr = "0" + hoursInt;
            }
        }

        return hoursStr + ":" + minutesStr + ":" + secondsStr;
    }
//__________________________________________________________________________________________________

    public static void checkForUpdateOnStartApp(Context context, View button) {

        new Thread(() -> {

            try {
                String versionJson = Jsoup.connect(context.getString(R.string.version_check_url))
                        .timeout(30000)
                        .get()
                        .body()
                        .text();

                JSONObject versionObject = new JSONObject(versionJson);

                if (BuildConfig.VERSION_CODE < Integer.parseInt(versionObject.getString("versionCode"))) {
                    new Handler(Looper.getMainLooper()).post(() -> button.setVisibility(View.VISIBLE));
                }
            } catch (Exception e) {
                Log.e(TAG, "error: ", e);
            }
        }).start();
    }

//    ----------------------------------------------------------------------------------------------

    public static void simpleAlert(Context context, String title, String message, String dismissBtn, Drawable drawableIcon) {

        AlertDialog.Builder builder = new AlertDialog.Builder(context);

        if (title != null) {
            builder.setTitle(title);
        }

        if (message != null) {
            builder.setMessage(message);
        }

        if (drawableIcon != null) {
            builder.setIcon(drawableIcon);
        }

        builder.setCancelable(false);

        builder.setPositiveButton(dismissBtn, (dialog, which) -> dialog.dismiss());

        AlertDialog alert = builder.create();

        alert.show();
    }
//    ----------------------------------------------------------------------------------------------

    public static void copyTextToClipboard(Context context, String text) {

        ClipboardManager clipboardManager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clipData = ClipData.newPlainText(null, text);

        if (clipboardManager != null) {

            clipboardManager.setPrimaryClip(clipData);
            Toast.makeText(context, "Copied to clipboard.", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(context, context.getString(R.string.report_issue_text), Toast.LENGTH_SHORT).show();
        }

    }
//    ----------------------------------------------------------------------------------------------
}
