package zoro.benojir.callrecorder.dialogs;

import android.app.Dialog;
import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import zoro.benojir.callrecorder.R;
import zoro.benojir.callrecorder.helpers.CustomFunctions;

import org.json.JSONException;
import org.json.JSONObject;

public class FileInfoDialog {

    private static final String TAG = "MADARA";
    private final Context context;
    private final JSONObject fileInfoJObj;

    public FileInfoDialog(Context context, JSONObject fileInfoJObj) {
        this.context = context;
        this.fileInfoJObj = fileInfoJObj;
    }

    public void show(){

        final Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);
        dialog.setContentView(R.layout.dialog_property_file);
        dialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);

        TextView fileSizeTV, fileLastModifiedTV, fileNameTV, filePathTV, fileDurationTV;

        fileSizeTV = dialog.findViewById(R.id.fileSizeTV);
        fileLastModifiedTV = dialog.findViewById(R.id.lastModifiedTV);
        fileNameTV = dialog.findViewById(R.id.fileNameTV);
        filePathTV = dialog.findViewById(R.id.filePathTV);
        fileDurationTV = dialog.findViewById(R.id.callDurationTV);

        try {
            fileSizeTV.setText(fileInfoJObj.get("size").toString());
            fileLastModifiedTV.setText(fileInfoJObj.getString("modified_date"));
            fileNameTV.setText(fileInfoJObj.getString("file_name"));
            filePathTV.setText(fileInfoJObj.getString("absolute_path"));

            try(MediaMetadataRetriever retriever = new MediaMetadataRetriever()) {
                retriever.setDataSource(fileInfoJObj.getString("absolute_path"));
                long duration = Long.parseLong(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
                retriever.release();
                int durationInSeconds = (int) (duration/1000f);
                fileDurationTV.setText(CustomFunctions.timeFormatterFromSeconds(durationInSeconds));
            } catch (Exception e) {
                Log.e(TAG, "showFileInfoDialog: ", e);
                fileDurationTV.setText(context.getString(R.string.unknown));
            }

            fileNameTV.setOnClickListener(view -> CustomFunctions.copyTextToClipboard(context, fileNameTV.getText().toString()));
            filePathTV.setOnClickListener(view -> CustomFunctions.copyTextToClipboard(context, filePathTV.getText().toString()));
        }
        catch (JSONException e) {
            dialog.dismiss();
            Toast.makeText(context, context.getString(R.string.report_issue_text), Toast.LENGTH_SHORT).show();
        }

        dialog.show();
    }
}
