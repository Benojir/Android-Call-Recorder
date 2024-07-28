package com.venomdino.callrecorder.helpers;

import android.app.Dialog;
import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.cardview.widget.CardView;

import com.venomdino.callrecorder.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;

public class ViewDialog {

    public void showFileInfoDialog(Context context, JSONObject fileInfoJObj){

        final Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);
        dialog.setContentView(R.layout.dialog_property_file);
        dialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);

        TextView fileSizeTV, fileLastModifiedTV, fileNameTV, filePathTV, fileDurationTV;
        CardView filePathCV, fileNameCV;

        fileSizeTV = dialog.findViewById(R.id.fileSizeTVinFilePropertyDialog);
        fileLastModifiedTV = dialog.findViewById(R.id.fileModifiedTVinFilePropertyDialog);
        fileNameTV = dialog.findViewById(R.id.fileNameTVinFilePropertyDialog);
        filePathTV = dialog.findViewById(R.id.fileLocationTVinFilePropertyDialog);
        fileDurationTV = dialog.findViewById(R.id.fileDurationTVinFilePropertyDialog);

        fileNameCV = dialog.findViewById(R.id.fileNameCVinFilePropertyDialog);
        filePathCV = dialog.findViewById(R.id.filePathCVinFilePropertyDialog);

        try {
            fileSizeTV.setText(fileInfoJObj.get("size").toString());
            fileLastModifiedTV.setText(fileInfoJObj.getString("modified_date"));
            fileNameTV.setText(fileInfoJObj.getString("name"));
            filePathTV.setText(fileInfoJObj.getString("absolute_path"));


            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            retriever.setDataSource(fileInfoJObj.getString("absolute_path"));
            long duration = Long.parseLong(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
            int durationInSeconds = (int) (duration/1000f);

            fileDurationTV.setText(CustomFunctions.timeFormatterFromSeconds(durationInSeconds));

            fileNameCV.setOnClickListener(view -> CustomFunctions.copyTextToClipboard(context, fileNameTV.getText().toString()));
            filePathCV.setOnClickListener(view -> CustomFunctions.copyTextToClipboard(context, filePathTV.getText().toString()));

        }
        catch (JSONException e) {
            e.printStackTrace();
            dialog.dismiss();
            Toast.makeText(context, context.getString(R.string.report_issue_text), Toast.LENGTH_SHORT).show();
        }

        dialog.show();
    }

    public void showRenameFileInputDialog(Context context, String oldName, String filePath) {

        final Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(false);
        dialog.setContentView(R.layout.dialog_rename_file);
        dialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);

        EditText newNameET = dialog.findViewById(R.id.new_file_name_et);

        newNameET.setText(oldName.substring(0, oldName.length() - 4));

        dialog.findViewById(R.id.rename_cancel_btn).setOnClickListener(view -> dialog.dismiss());

        dialog.findViewById(R.id.rename_confirm_btn).setOnClickListener(view -> {

            String newName = newNameET.getText().toString().trim();

            if (!newName.equalsIgnoreCase("")){

                newName += ".m4a";

                File oldNameFile = new File(new File(filePath).getParent().concat("/" + oldName));
                File newNameFile = new File(new File(filePath).getParent().concat("/" + newName));

                Log.d("MADARA", newName);

                if (oldNameFile.renameTo(newNameFile)){
                    Toast.makeText(context, "File renamed.", Toast.LENGTH_SHORT).show();
                }
                else{
                    Toast.makeText(context, "Failed", Toast.LENGTH_SHORT).show();
                }

                dialog.dismiss();
            }
            else{
                Toast.makeText(context, "Please provide a valid name.", Toast.LENGTH_SHORT).show();
            }

        });

        dialog.show();
    }
}
