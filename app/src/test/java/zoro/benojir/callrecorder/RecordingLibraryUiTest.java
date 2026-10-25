package zoro.benojir.callrecorder;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import zoro.benojir.callrecorder.adapters.RecordingsListAdapter;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, shadows = RecordingLibraryUiTest.HostFileProvider.class)
public class RecordingLibraryUiTest {
    /** AndroidX assumes Unix separators. Keep URI routing tests portable to Windows hosts. */
    @org.robolectric.annotation.Implements(value = androidx.core.content.FileProvider.class, isInAndroidSdk = false)
    public static class HostFileProvider {
        @org.robolectric.annotation.Implementation
        protected static android.net.Uri getUriForFile(android.content.Context context, String authority, File file) {
            android.content.pm.ProviderInfo provider = context.getPackageManager().resolveContentProvider(authority, 0);
            if (provider == null || provider.exported || !provider.grantUriPermissions)
                throw new IllegalArgumentException("Invalid recording FileProvider");
            File external = new File(context.getExternalFilesDir(null), "recordings");
            File internal = new File(context.getFilesDir(), "recordings");
            java.nio.file.Path path = file.toPath().toAbsolutePath().normalize();
            File root = path.startsWith(external.toPath().toAbsolutePath().normalize()) ? external : internal;
            if (!path.startsWith(root.toPath().toAbsolutePath().normalize()))
                throw new IllegalArgumentException("File outside recordings");
            return new android.net.Uri.Builder().scheme("content").authority(authority)
                    .appendPath("recordings").appendPath(file.getParentFile().getName()).appendPath(file.getName()).build();
        }
    }
    public static class LibraryActivity extends AppCompatActivity {
        RecyclerView list;
        @Override protected void onCreate(Bundle state) {
            setTheme(R.style.Theme_VoiceRecorder);
            super.onCreate(state);
            list = new RecyclerView(this);
            list.setLayoutManager(new LinearLayoutManager(this));
            setContentView(list);
        }
    }
    private static class TestAdapter extends RecordingsListAdapter {
        File played;
        TestAdapter(LibraryActivity activity, JSONArray rows) { super(activity, rows); }
        @Override protected void playInApp(File file) { played = file; }
    }
    private ActivityController<LibraryActivity> controller;
    private LibraryActivity activity;
    private TestAdapter adapter;
    private File first, second;
    @Before public void setup() throws Exception {
        controller = Robolectric.buildActivity(LibraryActivity.class).setup().visible();
        activity = controller.get();
        File folder = new File(new File(activity.getExternalFilesDir(null), "recordings"), "phone");
        assertTrue(folder.isDirectory() || folder.mkdirs());
        first = new File(folder, "first.m4a"); second = new File(folder, "second.m4a");
        try (FileOutputStream stream = new FileOutputStream(first)) { stream.write(1); }
        try (FileOutputStream stream = new FileOutputStream(second)) { stream.write(2); }
        adapter = new TestAdapter(activity, new JSONArray().put(row(first)).put(row(second)));
        activity.list.setAdapter(adapter);
        layout();
    }
    private JSONObject row(File file) throws Exception {
        return new JSONObject().put("file_name", file.getName()).put("absolute_path", file.getAbsolutePath())
                .put("modified_date", "Today").put("date_group", "Today").put("category", "phone")
                .put("size", "1 B").put("bytes", 1);
    }
    private void layout() {
        activity.list.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1800, View.MeasureSpec.EXACTLY));
        activity.list.layout(0, 0, 1080, 1800);
    }
    private View card(int position) {
        RecyclerView.ViewHolder holder = activity.list.findViewHolderForAdapterPosition(position);
        assertNotNull(holder);
        return holder.itemView.findViewById(R.id.mainLayout);
    }
    private void tap(int position) {
        View card = card(position);
        long now = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, card.getWidth()/2f, card.getHeight()/2f, 0);
        MotionEvent up = MotionEvent.obtain(now, now+30, MotionEvent.ACTION_UP, card.getWidth()/2f, card.getHeight()/2f, 0);
        card.dispatchTouchEvent(down); card.dispatchTouchEvent(up);
        down.recycle(); up.recycle();
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    }
    private Dialog options(int position) {
        assertTrue(card(position).performLongClick());
        Dialog dialog = ShadowDialog.getLatestDialog();
        assertNotNull(dialog); assertTrue(dialog.isShowing());
        return dialog;
    }
    private Intent chooserTarget() {
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        Intent chooser = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(chooser); assertEquals(Intent.ACTION_CHOOSER, chooser.getAction());
        return chooser.getParcelableExtra(Intent.EXTRA_INTENT);
    }
    @Test public void tappingCardContentInvokesInAppPlayer() { tap(0); assertEquals(first, adapter.played); }
    @Test public void longPressShowsEverySingleFileOption() {
        Dialog dialog = options(0);
        for (int id : new int[]{R.id.selectOption, R.id.playOption, R.id.shareOption, R.id.renameOption, R.id.deleteOption, R.id.showFileInfoOption})
            assertNotNull(dialog.findViewById(id));
    }
    @Test public void shareGrantsReadAccessToContentUri() {
        options(0).findViewById(R.id.shareOption).performClick();
        Intent share = chooserTarget();
        assertEquals(Intent.ACTION_SEND, share.getAction());
        assertEquals("content", ((android.net.Uri)share.getParcelableExtra(Intent.EXTRA_STREAM)).getScheme());
        assertNotEquals(0, share.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION);
    }
    @Test public void playWithOpensExternalPlayerChooser() {
        android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(activity, activity.getPackageName()+".provider", first);
        Intent external = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, "audio/*");
        android.content.pm.ResolveInfo player = new android.content.pm.ResolveInfo();
        player.activityInfo = new android.content.pm.ActivityInfo();
        player.activityInfo.packageName = "example.player"; player.activityInfo.name = "Player";
        player.activityInfo.exported = true; player.activityInfo.enabled = true;
        player.activityInfo.applicationInfo = new android.content.pm.ApplicationInfo();
        player.activityInfo.applicationInfo.packageName = "example.player";
        player.activityInfo.applicationInfo.enabled = true;
        Shadows.shadowOf(activity.getPackageManager()).addResolveInfoForIntent(external, player);
        options(0).findViewById(R.id.playOption).performClick();
        Intent target = chooserTarget();
        assertEquals(Intent.ACTION_VIEW, target.getAction()); assertEquals(uri, target.getData());
        assertNotEquals(0, target.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION);
    }
    @Test public void renameKeepsCategoryAndAppearsAfterFiltering() {
        options(0).findViewById(R.id.renameOption).performClick();
        AlertDialog rename = (AlertDialog)ShadowDialog.getLatestDialog();
        EditText field = findInput(rename.getWindow().getDecorView());
        assertNotNull(field); field.setText("renamed");
        rename.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertFalse(first.exists());
        assertTrue(new File(first.getParentFile(), "renamed.m4a").exists());
        adapter.filterLibrary("phone", "renamed"); assertEquals(1, adapter.getItemCount());
    }
    @Test public void deleteRemovesFileAndFilteredLibraryEntry() {
        options(0).findViewById(R.id.deleteOption).performClick();
        ((AlertDialog)ShadowDialog.getLatestDialog()).getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertFalse(first.exists()); assertEquals(1, adapter.libraryCount());
        adapter.filterLibrary("phone", "first"); assertEquals(0, adapter.getItemCount());
    }
    @Test public void propertiesShowsSelectedRecording() {
        options(0).findViewById(R.id.showFileInfoOption).performClick();
        Dialog properties = ShadowDialog.getLatestDialog();
        assertEquals(first.getAbsolutePath(), ((TextView)properties.findViewById(R.id.filePathTV)).getText().toString());
    }
    @Test public void multiSelectAndShareIncludesBothRecordings() {
        options(0).findViewById(R.id.selectOption).performClick();
        tap(1);
        assertNull(adapter.played);
        options(0).findViewById(R.id.shareAllOption).performClick();
        Intent share = chooserTarget();
        assertEquals(Intent.ACTION_SEND_MULTIPLE, share.getAction());
        ArrayList<android.net.Uri> files = share.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
        assertEquals(2, files.size());
        for (android.net.Uri file : files) assertEquals("content", file.getScheme());
    }
    private EditText findInput(View view) {
        if (view instanceof EditText) return (EditText)view;
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup)view;
            for (int i=0; i<group.getChildCount(); i++) { EditText found = findInput(group.getChildAt(i)); if (found != null) return found; }
        }
        return null;
    }
    @After public void cleanup() {
        for (Dialog dialog : ShadowDialog.getShownDialogs()) if (dialog.isShowing()) dialog.dismiss();
        controller.pause().stop().destroy();
    }
}
