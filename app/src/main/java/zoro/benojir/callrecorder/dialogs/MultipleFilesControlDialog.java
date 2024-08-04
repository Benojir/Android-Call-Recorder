package zoro.benojir.callrecorder.dialogs;

import android.app.Activity;
import android.app.Dialog;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import java.util.ArrayList;

import zoro.benojir.callrecorder.R;
import zoro.benojir.callrecorder.adapters.RecordingsListAdapter;

public class MultipleFilesControlDialog {
    private final Dialog dialog;
    private final ArrayList<Integer> selectedItemsPositionsList;
    private final TextView selectAllOption, deselectAllOption, shareAllOption, deleteAllOption;

    public MultipleFilesControlDialog(Activity activity, ArrayList<Integer> selectedItemsPositionsList) {
        this.selectedItemsPositionsList = selectedItemsPositionsList;

        dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);
        dialog.setContentView(R.layout.dialog_multiple_selected_files_options);
        dialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);

        selectAllOption = dialog.findViewById(R.id.selectAllOption);
        deselectAllOption = dialog.findViewById(R.id.deselectAllOption);
        shareAllOption = dialog.findViewById(R.id.shareAllOption);
        deleteAllOption = dialog.findViewById(R.id.deleteAllOption);
    }

    public void show(RecordingsListAdapter.OnMultipleItemsLongClickListener listener) {
        actionsOnDialog(listener);
        dialog.show();
    }

    private void actionsOnDialog(RecordingsListAdapter.OnMultipleItemsLongClickListener listener) {
        selectAllOption.setOnClickListener(view -> {
            listener.onSelectAllOptionClicked();
            dialog.dismiss();
        });

        deselectAllOption.setOnClickListener(view -> {
            listener.onDeselectAllOptionClicked(selectedItemsPositionsList);
            dialog.dismiss();
        });

        shareAllOption.setOnClickListener(view -> {
            listener.onShareAllOptionClicked(selectedItemsPositionsList);
            dialog.dismiss();
        });

        deleteAllOption.setOnClickListener(view -> {
            listener.onDeleteAllOptionClicked();
            dialog.dismiss();
        });
    }
}
