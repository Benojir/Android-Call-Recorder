package zoro.benojir.callrecorder.activities;

import android.view.View;
import android.view.WindowManager;
import androidx.appcompat.app.AppCompatActivity;
import zoro.benojir.callrecorder.helpers.AppLock;

/** Covers both the launcher and exported Settings shortcut, including process recreation. */
public abstract class LockedActivity extends AppCompatActivity {
    private final java.util.List<java.lang.ref.WeakReference<android.app.Dialog>> dialogs = new java.util.ArrayList<>();
    private static int visibleActivities;
    private boolean prompting;
    @Override protected void onStart() { super.onStart(); visibleActivities++; }
    @Override protected void onResume() {
        super.onResume();
        if (AppLock.enabled(this)) getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        if (AppLock.needsUnlock(this)) {
            findViewById(android.R.id.content).setVisibility(View.INVISIBLE);
            if (!prompting) {
                prompting = true;
                AppLock.prompt(this, false, () -> {
                    prompting = false;
                    findViewById(android.R.id.content).setVisibility(View.VISIBLE);
                    onAppUnlocked();
                }, () -> { prompting = false; finish(); });
            }
        } else {
            findViewById(android.R.id.content).setVisibility(View.VISIBLE);
            onAppUnlocked();
        }
    }
    protected void onAppUnlocked() { }
    public void trackDialog(android.app.Dialog dialog) {
        dialogs.add(new java.lang.ref.WeakReference<>(dialog));
        if (AppLock.enabled(this) && dialog.getWindow() != null) dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    }
    @Override protected void onStop() {
        super.onStop();
        visibleActivities = Math.max(0, visibleActivities - 1);
        if (visibleActivities == 0 && !isChangingConfigurations()) AppLock.lock();
        if (AppLock.enabled(this)) findViewById(android.R.id.content).setVisibility(View.INVISIBLE);
        prompting = false;
        for (java.lang.ref.WeakReference<android.app.Dialog> reference : dialogs) {
            android.app.Dialog dialog = reference.get();
            if (dialog != null && dialog.isShowing()) dialog.dismiss();
        }
        dialogs.clear();
    }
}
