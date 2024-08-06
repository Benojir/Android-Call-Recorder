package zoro.benojir.callrecorder.helpers;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.text.InputType;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;

import zoro.benojir.callrecorder.R;

public class SingleFileOptionsHelper {

    private static final String TAG = "MADARA";
    private final File file;
    private final Activity activity;

    public SingleFileOptionsHelper(Activity activity, File file) {
        this.activity = activity;
        this.file = file;
    }

//    ----------------------------------------------------------------------------------------------

    public void playRecording() {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri uri = FileProvider.getUriForFile(activity, activity.getApplicationContext().getPackageName() + ".provider", file);
            intent.setDataAndType(uri, "audio/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            // Create a chooser intent
            Intent chooserIntent = Intent.createChooser(intent, "Choose an app to play audio");

            // Verify that there are applications available to handle the intent
            if (intent.resolveActivity(activity.getPackageManager()) != null) {
                activity.startActivity(chooserIntent);
            } else {
                // Handle the case where no suitable app is installed
                Toast.makeText(activity, "No app available to play audio", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error playing audio: ", e);
        }
    }

//    ----------------------------------------------------------------------------------------------

    public void shareSingleRecordingFile() {
        try {
            Intent intent = new Intent(Intent.ACTION_SEND);
            Uri uri = FileProvider.getUriForFile(activity, activity.getApplicationContext().getPackageName() + ".provider", file);
            intent.setDataAndType(uri, "audio/mpeg");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            activity.startActivity(Intent.createChooser(intent, "Share via"));
        } catch (Exception e) {
            Log.e(TAG, "onBindViewHolder: ", e);
        }
    }

//    ----------------------------------------------------------------------------------------------

    public void renameFile(OnFileRenamedListener listener) {
        String oldName = file.getName();
        String filePath = file.getAbsolutePath();

        EditText inputField = new EditText(activity);
        inputField.setHint("Enter new file name...");
        inputField.setText(oldName.substring(0, oldName.length() - 4));
        inputField.setSingleLine(true);
        inputField.setInputType(InputType.TYPE_CLASS_TEXT);
        if (CustomFunctions.isDarkModeOn(activity)) {
            inputField.setBackgroundTintList(ColorStateList.valueOf(activity.getColor(R.color.theme_color_dark)));
        } else {
            inputField.setBackgroundTintList(ColorStateList.valueOf(activity.getColor(R.color.theme_color_light)));
        }
        inputField.setPadding(
                activity.getResources().getDimensionPixelSize(R.dimen.input_box_padding_lr),
                activity.getResources().getDimensionPixelSize(R.dimen.input_box_padding_tb),
                activity.getResources().getDimensionPixelSize(R.dimen.input_box_padding_lr),
                activity.getResources().getDimensionPixelSize(R.dimen.input_box_padding_tb)
        );

        FrameLayout container = new FrameLayout(activity);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.leftMargin = activity.getResources().getDimensionPixelSize(R.dimen.dialog_margin_left);
        params.rightMargin = activity.getResources().getDimensionPixelSize(R.dimen.dialog_margin_right);
        params.topMargin = activity.getResources().getDimensionPixelSize(R.dimen.dialog_margin_top);

        inputField.setLayoutParams(params);

        container.addView(inputField);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Rename to:")
                .setView(container)
                .setCancelable(false)
                .setIcon(R.drawable.mode_edit_24)
                .setPositiveButton("Rename", (dialogInterface, i) -> {

                    String newName = inputField.getText().toString();

                    if (!newName.equalsIgnoreCase("")) {

                        newName += ".m4a";

                        File oldNameFile = new File(filePath);
                        File newNameFile = new File(oldNameFile.getParent().concat("/" + newName));

                        JSONObject tempFileJObj = new JSONObject();

                        if (oldNameFile.renameTo(newNameFile)) {

                            Toast.makeText(activity, "File renamed.", Toast.LENGTH_SHORT).show();

                            try {
                                tempFileJObj.put("file_name", newName);
                                tempFileJObj.put("size", CustomFunctions.fileSizeFormatter(newNameFile.length()));
                                tempFileJObj.put("modified_date", CustomFunctions.timeFormatter(newNameFile.lastModified()));
                                tempFileJObj.put("absolute_path", newNameFile.getAbsolutePath());
                                listener.onComplete(tempFileJObj, true);
                            } catch (JSONException e) {
                                try {
                                    listener.onComplete(tempFileJObj, false);
                                } catch (JSONException ex) {
                                    Log.e(TAG, "renameFile: ", e);
                                }
                                Log.e(TAG, "onBindViewHolder: ", e);
                            }
                        } else {
                            try {
                                listener.onComplete(tempFileJObj, false);
                            } catch (JSONException e) {
                                Log.e(TAG, "renameFile: ", e);
                            }
                            Toast.makeText(activity, "Failed", Toast.LENGTH_SHORT).show();
                        }

                        dialogInterface.dismiss();
                    } else {
                        Toast.makeText(activity, "Please provide a valid name.", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .create();

        dialog.show();
    }

//    ----------------------------------------------------------------------------------------------

    public void deleteFile(OnFileDeletedListener listener){

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setCancelable(true);
        builder.setTitle("Delete");
        builder.setMessage("Do you really want to delete?");
        builder.setIcon(R.drawable.delete_24);
        builder.setPositiveButton("Delete", (dialogInterface, i) -> {

            if (file.delete()) {
                Toast.makeText(activity, "Recorded file deleted successfully.", Toast.LENGTH_SHORT).show();
                listener.onComplete(true, file);
            } else {
                Toast.makeText(activity, "Failed to delete file.", Toast.LENGTH_SHORT).show();
                listener.onComplete(false, file);
            }
        });
        builder.setNegativeButton("Cancel", (dialogInterface, i) -> dialogInterface.dismiss());
        builder.show();
    }

//    ----------------------------------------------------------------------------------------------

    public interface OnFileRenamedListener {
        void onComplete(JSONObject fileInfo, boolean success) throws JSONException;
    }
    public interface OnFileDeletedListener {
        void onComplete(boolean success, File file);
    }
}
