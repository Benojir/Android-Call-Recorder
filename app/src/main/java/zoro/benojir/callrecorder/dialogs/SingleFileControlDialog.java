package zoro.benojir.callrecorder.dialogs;

import android.app.Activity;
import android.app.Dialog;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import zoro.benojir.callrecorder.R;
import zoro.benojir.callrecorder.adapters.RecordingsListAdapter;

public class SingleFileControlDialog {

    private final JSONObject fileInfo;
    private final Dialog dialog;
    private final TextView fileNameTV, selectOption, playOption, shareOption, renameOption, deleteOption, fileInfoOption;
    private final int itemPosition;

    public SingleFileControlDialog(Activity activity, JSONObject fileInfo, int position) {
        this.fileInfo = fileInfo;
        this.itemPosition = position;

        dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);
        dialog.setContentView(R.layout.dialog_single_file_options);
        dialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);

        fileNameTV = dialog.findViewById(R.id.fileNameTV);

        selectOption = dialog.findViewById(R.id.selectOption);
        playOption = dialog.findViewById(R.id.playOption);
        shareOption = dialog.findViewById(R.id.shareOption);
        renameOption = dialog.findViewById(R.id.renameOption);
        deleteOption = dialog.findViewById(R.id.deleteOption);
        fileInfoOption = dialog.findViewById(R.id.showFileInfoOption);
    }

    public void show(RecordingsListAdapter.OnSingleItemLongClickListener singleItemLongClickListener) throws JSONException {
        String fileName = fileInfo.getString("file_name");
        fileNameTV.setText(fileName);
        actionsOnDialog(singleItemLongClickListener);
        dialog.show();
    }

    private void actionsOnDialog(RecordingsListAdapter.OnSingleItemLongClickListener singleItemLongClickListener) {

        selectOption.setOnClickListener(view -> {
            singleItemLongClickListener.onSelectOptionClicked(itemPosition);
            dialog.dismiss();
        });

        playOption.setOnClickListener(view -> {
            singleItemLongClickListener.onPlayWithOptionClicked(itemPosition);
            dialog.dismiss();
        });

        shareOption.setOnClickListener(view -> {
            singleItemLongClickListener.onShareOptionClicked(itemPosition);
            dialog.dismiss();
        });

        renameOption.setOnClickListener(view -> {
            singleItemLongClickListener.onRenameOptionClicked(itemPosition);
            dialog.dismiss();
        });

        deleteOption.setOnClickListener(view -> {
            singleItemLongClickListener.onDeleteOptionClicked(itemPosition);
            dialog.dismiss();
        });

        fileInfoOption.setOnClickListener(view -> {
            singleItemLongClickListener.onShowFileInfoOptionClicked(itemPosition);
            dialog.dismiss();
        });
    }
}
