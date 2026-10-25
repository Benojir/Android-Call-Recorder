package zoro.benojir.callrecorder.helpers;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.InputType;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Private salted PIN verifier; recordings and the verifier are excluded from Android backup. */
public final class AppLock {
    private static boolean unlocked;
    private static final ExecutorService worker = Executors.newSingleThreadExecutor();
    private AppLock() { }
    private static SharedPreferences prefs(Context context) { return context.getSharedPreferences("app_lock", Context.MODE_PRIVATE); }
    public static boolean enabled(Context context) { return prefs(context).contains("hash"); }
    public static boolean needsUnlock(Context context) { return enabled(context) && !unlocked; }
    public static void lock() { unlocked = false; }
    public static void track(Context context, android.app.Dialog dialog) {
        if (context instanceof zoro.benojir.callrecorder.activities.LockedActivity)
            ((zoro.benojir.callrecorder.activities.LockedActivity) context).trackDialog(dialog);
    }
    public static void prompt(Activity activity, boolean setup, Runnable success, Runnable cancel) {
        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setHint(setup ? "6–12 digit PIN" : "PIN");
        input.setPadding(48, 24, 48, 24);
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle(setup ? "Create app PIN" : "Unlock Call Recorder")
                .setMessage(setup ? "Choose a PIN. You will confirm it next. Forgotten PINs cannot be recovered."
                        : "Enter your app PIN to access recordings and settings.")
                .setView(input).setCancelable(false).setPositiveButton(setup ? "Continue" : "Unlock", null)
                .setNegativeButton("Cancel", (d, which) -> cancel.run()).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String pin = input.getText().toString();
            if (setup) {
                if (!PinCrypto.valid(pin)) { input.setError("Use 6–12 digits"); return; }
                dialog.dismiss();
                confirm(activity, pin, success, cancel);
                return;
            }
            SharedPreferences preferences = prefs(activity);
            long remaining = preferences.getLong("blocked_until", 0) - System.currentTimeMillis();
            if (remaining > 0) { input.setError("Try again in " + (remaining / 1000 + 1) + " seconds"); return; }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            worker.execute(() -> {
                boolean match = false;
                try {
                    match = PinCrypto.matches(PinCrypto.derive(pin.toCharArray(), Base64.getDecoder().decode(preferences.getString("salt", ""))),
                            Base64.getDecoder().decode(preferences.getString("hash", "")));
                } catch (Exception ignored) { }
                final boolean verified = match;
                activity.runOnUiThread(() -> {
                    if (!visible(activity) || !dialog.isShowing()) { unlocked = false; return; }
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                    input.setText("");
                    if (verified) {
                        preferences.edit().remove("failures").remove("blocked_until").apply();
                        unlocked = true;
                        dialog.dismiss();
                        success.run();
                    } else {
                        int failures = preferences.getInt("failures", 0) + 1;
                        preferences.edit().putInt("failures", failures).putLong("blocked_until",
                                failures >= 5 ? System.currentTimeMillis() + 60000 : 0).apply();
                        input.setError(failures >= 5 ? "Too many attempts. Wait 60 seconds." : "Incorrect PIN");
                    }
                });
            });
        }));
        dialog.show();
        track(activity, dialog);
        dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    }
    private static void confirm(Activity activity, String pin, Runnable success, Runnable cancel) {
        EditText input = new EditText(activity);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setSingleLine(true);
        input.setPadding(48, 24, 48, 24);
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("Confirm app PIN").setView(input)
                .setCancelable(false).setPositiveButton("Enable lock", null).setNegativeButton("Cancel", (d, w) -> cancel.run()).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (!pin.equals(input.getText().toString())) { input.setError("PINs do not match"); return; }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            worker.execute(() -> {
                try {
                    byte[] salt = new byte[16];
                    new SecureRandom().nextBytes(salt);
                    String hash = Base64.getEncoder().encodeToString(PinCrypto.derive(pin.toCharArray(), salt));
                    boolean saved = prefs(activity).edit().putString("salt", Base64.getEncoder().encodeToString(salt))
                            .putString("hash", hash).remove("failures").remove("blocked_until").commit();
                    activity.runOnUiThread(() -> {
                        if (!visible(activity) || !dialog.isShowing()) { unlocked = false; return; }
                        dialog.dismiss();
                        if (saved) { unlocked = true; success.run(); }
                        else { Toast.makeText(activity, "Could not save app lock", Toast.LENGTH_LONG).show(); cancel.run(); }
                    });
                } catch (Exception e) { activity.runOnUiThread(() -> { dialog.dismiss(); cancel.run(); }); }
            });
        }));
        dialog.show();
        track(activity, dialog);
        dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    }
    public static void disable(Context context) { prefs(context).edit().clear().apply(); unlocked = false; }
    private static boolean visible(Activity activity) {
        return !activity.isFinishing() && !activity.isDestroyed()
                && activity instanceof androidx.lifecycle.LifecycleOwner
                && ((androidx.lifecycle.LifecycleOwner) activity).getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED);
    }
}
