package zoro.benojir.callrecorder.dialogs;

import android.app.Dialog;
import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;

import zoro.benojir.callrecorder.R;
import zoro.benojir.callrecorder.helpers.CustomFunctions;

public class FileInfoDialog {

    private final Context context;

    public FileInfoDialog(Context context, JSONObject fileInfoJObj) {
        this.context = context;

        final Dialog dialog = new Dialog(context);
        zoro.benojir.callrecorder.helpers.AppLock.track(context, dialog);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);
        dialog.setContentView(R.layout.dialog_file_properties);
        dialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);

        TextView fileSizeTV, fileLastModifiedTV, fileNameTV, filePathTV, fileDurationTV;

        fileSizeTV = dialog.findViewById(R.id.fileSizeTV);
        fileLastModifiedTV = dialog.findViewById(R.id.lastModifiedTV);
        fileNameTV = dialog.findViewById(R.id.fileNameTV);
        filePathTV = dialog.findViewById(R.id.filePathTV);
        fileDurationTV = dialog.findViewById(R.id.callDurationTV);

        try {
            fileNameTV.setText(fileInfoJObj.getString("file_name"));
            fileSizeTV.setText(fileInfoJObj.get("size").toString());
            fileLastModifiedTV.setText(fileInfoJObj.getString("modified_date"));
            filePathTV.setText(fileInfoJObj.getString("absolute_path"));

            // Read metadata without allocating a playback engine or audio session.
            File audioFile = new File(fileInfoJObj.getString("absolute_path"));
            getAudioDuration(audioFile, fileDurationTV);

            fileNameTV.setOnClickListener(view -> CustomFunctions.copyTextToClipboard(context, fileNameTV.getText().toString()));
            filePathTV.setOnClickListener(view -> CustomFunctions.copyTextToClipboard(context, filePathTV.getText().toString()));
        }
        catch (JSONException e) {
            dialog.dismiss();
            Toast.makeText(context, context.getString(R.string.report_issue_text), Toast.LENGTH_SHORT).show();
        }

        dialog.show();
    }

    private void getAudioDuration(File audioFile, TextView fileDurationTV) {
        try (MediaMetadataRetriever metadata = new MediaMetadataRetriever()) {
            metadata.setDataSource(audioFile.getAbsolutePath());
            String duration = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            fileDurationTV.setText(duration == null ? context.getString(R.string.unknown)
                    : CustomFunctions.formatDuration(Long.parseLong(duration)));
        } catch (Exception e) { fileDurationTV.setText(context.getString(R.string.unknown)); }
    }
}
