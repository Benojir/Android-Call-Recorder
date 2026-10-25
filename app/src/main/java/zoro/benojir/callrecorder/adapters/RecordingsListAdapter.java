package zoro.benojir.callrecorder.adapters;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.core.content.FileProvider;
import zoro.benojir.callrecorder.helpers.RecordingCategory;
import java.util.Locale;

import zoro.benojir.callrecorder.R;
import zoro.benojir.callrecorder.activities.MainActivity;
import zoro.benojir.callrecorder.dialogs.AudioPlayerDialog;
import zoro.benojir.callrecorder.dialogs.FileInfoDialog;
import zoro.benojir.callrecorder.dialogs.MultipleFilesControlDialog;
import zoro.benojir.callrecorder.dialogs.SingleFileControlDialog;
import zoro.benojir.callrecorder.helpers.SingleFileOptionsHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;

public class RecordingsListAdapter extends RecyclerView.Adapter<RecordingsListAdapter.MyCustomViewHolder> implements Filterable {

    private static final String TAG = "MADARA";
    private final Activity activity;
    private JSONArray fileInfos;
    private final JSONArray fileInfos2;

    private boolean isSelectionModeOn;
    private final ArrayList<Integer> selectedItemsPositionsList = new ArrayList<>();
    private final ArrayList<Uri> selectedFilesUriList = new ArrayList<>();

    public RecordingsListAdapter(Activity activity, JSONArray fileInfos) {
        this.activity = activity;
        this.fileInfos = new JSONArray();
        for (int i = 0; i < fileInfos.length(); i++) this.fileInfos.put(fileInfos.opt(i));
        this.fileInfos2 = fileInfos;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void filterLibrary(String category, String query) {
        selectedItemsPositionsList.clear();
        selectedFilesUriList.clear();
        isSelectionModeOn = false;
        disableSelectedItemCountMenu();
        String term = query.trim().toLowerCase(Locale.ROOT);
        JSONArray result = new JSONArray();
        for (int i = 0; i < fileInfos2.length(); i++) {
            JSONObject item = fileInfos2.optJSONObject(i);
            if (item != null && ("all".equals(category) || category.equals(item.optString("category", "phone")))
                    && (item.optString("file_name").toLowerCase(Locale.ROOT).contains(term)
                    || item.optString("modified_date").toLowerCase(Locale.ROOT).contains(term)
                    || RecordingCategory.label(item.optString("category", "phone")).toLowerCase(Locale.ROOT).contains(term))) result.put(item);
        }
        fileInfos = result;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public MyCustomViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater layoutInflater = LayoutInflater.from(activity);
        View view = layoutInflater.inflate(R.layout.sample_recording_item_design, parent, false);
        return new MyCustomViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MyCustomViewHolder holder, int position) {

        try {
            JSONObject row = fileInfos.getJSONObject(position);
            String fileName = row.optString("display_name", row.getString("file_name"));
            String modifiedDate = row.getString("modified_date");
            String fileSize = row.getString("size");
            holder.categoryTV.setText(RecordingCategory.label(row.optString("category", "phone")));
            String group = row.optString("date_group", "Recordings");
            holder.dateHeader.setText(group);
            holder.dateHeader.setVisibility(position == 0 || !group.equals(fileInfos.getJSONObject(position - 1).optString("date_group")) ? View.VISIBLE : View.GONE);

            String fileSizeAndDate = modifiedDate + "\t\t(" + fileSize + ")";

            holder.fileNameTV.setText(fileName);
            holder.fileInfoTV.setText(fileSizeAndDate);

            File file = new File(fileInfos.getJSONObject(holder.getAdapterPosition()).getString("absolute_path"));
            SingleFileOptionsHelper fileOptionsHelper = new SingleFileOptionsHelper(activity, file);

            //................................................................

            holder.itemView.setOnClickListener(view -> {

                if (isSelectionModeOn) {

                    if (selectedItemsPositionsList.contains(holder.getAdapterPosition())) {

                        selectedItemsPositionsList.remove((Integer) holder.getAdapterPosition());
                        removeUnselectedItemUI(holder);
                        selectedFilesUriList.remove(fileUri(file));
                        enableSelectedItemCountMenu();

                        if (selectedItemsPositionsList.isEmpty()) {
                            isSelectionModeOn = false;
                            disableSelectedItemCountMenu();
                        }
                    } else {
                        selectedItemsPositionsList.add(holder.getAdapterPosition());
                        setSelectedItemUI(holder);
                        selectedFilesUriList.add(fileUri(file));
                        enableSelectedItemCountMenu();
                    }
                } else {
                    new AudioPlayerDialog(activity, file);
                }
            });

            //................................................................

            if (isSelectionModeOn) {
                enableSelectedItemCountMenu();
            }

            // Update selection state here for all selected items. Because onDeselectAllOptionClicked will not trigger when onBindViewHolder is called.
            if (selectedItemsPositionsList.contains(holder.getAdapterPosition())) {
                setSelectedItemUI(holder);
            } else {
                removeUnselectedItemUI(holder);

                if (selectedItemsPositionsList.isEmpty()) {
                    isSelectionModeOn = false;
                    disableSelectedItemCountMenu();
                }
            }

            //................................................................

            holder.itemView.setOnLongClickListener(view -> {
                try {
                    if (isSelectionModeOn) {
                        MultipleFilesControlDialog multipleFilesControlDialog = new MultipleFilesControlDialog(activity, selectedItemsPositionsList);

                        multipleFilesControlDialog.show(new OnMultipleItemsLongClickListener() {
                            @SuppressLint("NotifyDataSetChanged")
                            @Override
                            public void onSelectAllOptionClicked() {
                                isSelectionModeOn = true;
                                selectAllItems();
                            }

                            @SuppressLint("NotifyDataSetChanged")
                            @Override
                            public void onDeselectAllOptionClicked(ArrayList<Integer> selectedItemsPositionsList) {
                                deselectAllItems();
                            }

                            @Override
                            public void onShareAllOptionClicked(ArrayList<Integer> selectedItemsPositionsList) {
                                Intent intent = new Intent();
                                intent.setAction(Intent.ACTION_SEND_MULTIPLE);
                                intent.putExtra(Intent.EXTRA_SUBJECT, "Here are some files.");
                                intent.setType("audio/*");
                                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, selectedFilesUriList);
                                activity.startActivity(Intent.createChooser(intent, "Share to"));
                            }

                            @Override
                            public void onDeleteAllOptionClicked() {
                                deleteAllSelectedItems();
                            }
                        });

                    } else {
                        JSONObject fileInfo = fileInfos.getJSONObject(holder.getAdapterPosition());
                        SingleFileControlDialog singleFileControlDialog = new SingleFileControlDialog(activity, fileInfo, holder.getAdapterPosition());

                        singleFileControlDialog.show(new OnSingleItemLongClickListener() {
                            @Override
                            public void onSelectOptionClicked(int position) {

                                isSelectionModeOn = true;
                                selectedItemsPositionsList.add(holder.getAdapterPosition());
                                setSelectedItemUI(holder);
                                selectedFilesUriList.add(fileUri(file));
                                enableSelectedItemCountMenu();
                            }

                            @Override
                            public void onPlayWithOptionClicked(int position) {
                                fileOptionsHelper.playRecording();
                            }

                            @Override
                            public void onShareOptionClicked(int position) {
                                fileOptionsHelper.shareSingleRecordingFile();
                            }

                            @Override
                            public void onRenameOptionClicked(int position) {
                                fileOptionsHelper.renameFile((fileInfoJObject, success) -> {
                                    if (success) {
                                        for (int i = 0; i < fileInfos2.length(); i++) {
                                            if (file.getAbsolutePath().equals(fileInfos2.optJSONObject(i).optString("absolute_path"))) {
                                                fileInfos2.put(i, fileInfoJObject);
                                                break;
                                            }
                                        }
                                        fileInfos.put(position, fileInfoJObject);
                                        notifyItemChanged(position);
                                    } else {
                                        Toast.makeText(activity, "Failed to rename file.", Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }

                            @Override
                            public void onDeleteOptionClicked(int position) {

                                fileOptionsHelper.deleteFile((success, file1) -> {

                                    if (success) {

                                        fileInfos.remove(position);
                                        selectedItemsPositionsList.remove((Integer) position);

                                        for (int i = 0; i < fileInfos2.length(); i++) {
                                            try {
                                                if (fileInfos2.getJSONObject(i).getString("absolute_path").equals(file1.getAbsolutePath())) {
                                                    fileInfos2.remove(i);
                                                    break;
                                                }
                                            } catch (JSONException e) {
                                                Log.e(TAG, "onDeleteOptionClicked: ", e);
                                            }
                                        }
                                        notifyDataSetChanged();
                                    } else {
                                        Toast.makeText(activity, "Failed to delete file.", Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }

                            @Override
                            public void onShowFileInfoOptionClicked(int position) {
                                new FileInfoDialog(activity, fileInfo);
                            }
                        });
                    }
                } catch (Exception e) {
                    Log.e(TAG, "onBindViewHolder: ", e);
                }
                return true;
            });

            //................................................................
        } catch (JSONException e) {
            Log.e(TAG, "onBindViewHolder: ", e);
        }
    }

    @Override
    public int getItemCount() {
        return fileInfos.length();
    }

    public int libraryCount() { return fileInfos2.length(); }
    public long libraryBytes() {
        long size = 0;
        for (int i = 0; i < fileInfos2.length(); i++) size += fileInfos2.optJSONObject(i).optLong("bytes");
        return size;
    }

    @Override
    public Filter getFilter() {

        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence charSequence) {

                JSONArray filteredFiles = new JSONArray();

                if (charSequence == null || charSequence.length() == 0) {
                    filteredFiles = fileInfos2;
                } else {
                    String searchTerm = charSequence.toString().trim().toLowerCase();

                    for (int i = 0; i < fileInfos2.length(); i++) {

                        try {
                            JSONObject fileInfoJObj = fileInfos2.getJSONObject(i);
                            boolean doesNameContainSearchTerm = fileInfoJObj.getString("file_name").toLowerCase().contains(searchTerm);
                            boolean doesModifiedDateContainSearchTerm = fileInfoJObj.getString("modified_date").toLowerCase().contains(searchTerm);

                            if (doesNameContainSearchTerm || doesModifiedDateContainSearchTerm) {
                                filteredFiles.put(fileInfoJObj);
                            }
                        } catch (JSONException e) {
                            Log.e(TAG, "onBindViewHolder: ", e);
                        }
                    }
                }

                FilterResults filterResults = new FilterResults();
                filterResults.values = filteredFiles;
                filterResults.count = filteredFiles.length();

                return filterResults;
            }

            @SuppressLint("NotifyDataSetChanged")
            @Override
            protected void publishResults(CharSequence charSequence, FilterResults filterResults) {
                fileInfos = (JSONArray) filterResults.values;
                notifyDataSetChanged();
            }
        };
    }

    public static class MyCustomViewHolder extends RecyclerView.ViewHolder {

        private final RelativeLayout mainLayout;
        private final TextView fileNameTV;
        private final TextView fileInfoTV;
        private final ImageView selectionIcon;
        private final TextView categoryTV;
        private final TextView dateHeader;

        public MyCustomViewHolder(View itemView) {
            super(itemView);
            mainLayout = itemView.findViewById(R.id.mainLayout);
            fileNameTV = itemView.findViewById(R.id.fileNameTV);
            fileInfoTV = itemView.findViewById(R.id.fileInfoTV);
            selectionIcon = itemView.findViewById(R.id.selectionIcon);
            categoryTV = itemView.findViewById(R.id.recordingCategory);
            dateHeader = itemView.findViewById(R.id.dateHeader);
        }
    }

//    ----------------------------------------------------------------------------------------------

    private void setSelectedItemUI(MyCustomViewHolder holder) {
        holder.mainLayout.setBackgroundColor(activity.getColor(R.color.fade_blue));
        holder.selectionIcon.setVisibility(View.VISIBLE);
    }

    private void removeUnselectedItemUI(MyCustomViewHolder holder) {
        holder.mainLayout.setBackgroundColor(Color.TRANSPARENT);
        holder.selectionIcon.setVisibility(View.GONE);
    }

//    ----------------------------------------------------------------------------------------------

    private void selectAllItems() {
        new Thread(() -> {

            selectedItemsPositionsList.clear();
            selectedFilesUriList.clear();

            for (int i = 0; i < fileInfos.length(); i++) {

                selectedItemsPositionsList.add(i);

                try {
                    File file = new File(fileInfos.getJSONObject(i).getString("absolute_path"));
                    selectedFilesUriList.add(fileUri(file));
                } catch (Exception e) {
                    Log.e(TAG, "onBindViewHolder: ", e);
                }
            }
            activity.runOnUiThread(new Runnable() {
                @SuppressLint("NotifyDataSetChanged")
                @Override
                public void run() {
                    notifyDataSetChanged();
                }
            });
        }).start();
    }

    private void deselectAllItems() {
        new Thread(() -> {

            selectedItemsPositionsList.clear();
            selectedFilesUriList.clear();

            activity.runOnUiThread(new Runnable() {
                @SuppressLint("NotifyDataSetChanged")
                @Override
                public void run() {
                    notifyDataSetChanged();
                }
            });
        }).start();
    }

//    ----------------------------------------------------------------------------------------------

    /**
     * Why reverse deleting?
     * While modifying the list itself it remove an item from a list while iterating over it, it can cause
     * index shifting, which might lead to skipping elements.
     * To avoid this issue, you can iterate over the list in reverse order,
     * which ensures that removing an item does not affect the subsequent elements that need to be removed.
     */

    @SuppressLint("NotifyDataSetChanged")
    private void deleteAllSelectedItems() {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setCancelable(true);
        builder.setTitle("Delete files");
        builder.setMessage("Selected files will be deleted.");
        builder.setIcon(R.drawable.delete_24);
        builder.setPositiveButton("Delete", (dialogInterface, which) -> {

            dialogInterface.dismiss();

            ProgressDialog pd = new ProgressDialog(activity);
            pd.setCancelable(false);
            pd.setMessage("Deleting files...");
            pd.show();
            zoro.benojir.callrecorder.helpers.AppLock.track(activity, pd);

            Collections.sort(selectedItemsPositionsList);
            new Thread(() -> {

                // Iterate in reverse order to avoid index shifting issues
                for (int i = selectedItemsPositionsList.size() - 1; i >= 0; i--) {
                    try {
                        int position = selectedItemsPositionsList.get(i);

                        File file = new File(fileInfos.getJSONObject(position).getString("absolute_path"));

                        if (file.delete()) {
                            fileInfos.remove(position);
                            selectedItemsPositionsList.remove(i);
                            selectedFilesUriList.remove(i);

                            for (int j = 0; j < fileInfos2.length(); j++) {
                                if (fileInfos2.getJSONObject(j).getString("absolute_path").equals(file.getAbsolutePath())) {
                                    fileInfos2.remove(j);
                                    break;
                                }
                            }
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "deleteAllSelectedItems: ", e);
                    }
                }

                activity.runOnUiThread(() -> {
                    pd.dismiss();
                    // Notify RecyclerView about data changes
                    notifyDataSetChanged();
                    isSelectionModeOn = false;
                    disableSelectedItemCountMenu();
                });
            }).start();
        });
        builder.setNegativeButton("Cancel", (dialogInterface, which) -> dialogInterface.dismiss());
        zoro.benojir.callrecorder.helpers.AppLock.track(activity, builder.show());
    }

//    ----------------------------------------------------------------------------------------------

    private void enableSelectedItemCountMenu() {
        if (MainActivity.searchBtn == null || MainActivity.settingsBtn == null || MainActivity.selectedItemsCountMenu == null) return;
        MainActivity.searchBtn.setVisible(false);
        MainActivity.settingsBtn.setVisible(false);
        MainActivity.selectedItemsCountMenu.setVisible(true);
        MainActivity.selectedItemsCountMenu.setTitle(selectedItemsPositionsList.size() + "");
    }

    private Uri fileUri(File file) {
        return FileProvider.getUriForFile(activity, activity.getPackageName() + ".provider", file);
    }

    private void disableSelectedItemCountMenu() {
        if (MainActivity.searchBtn != null && MainActivity.settingsBtn != null && MainActivity.selectedItemsCountMenu != null) {
            MainActivity.searchBtn.setVisible(true);
            MainActivity.settingsBtn.setVisible(true);
            MainActivity.selectedItemsCountMenu.setVisible(false);
        }
    }
//    ----------------------------------------------------------------------------------------------

    public interface OnSingleItemLongClickListener {
        void onSelectOptionClicked(int position);

        void onPlayWithOptionClicked(int position);

        void onShareOptionClicked(int position);

        void onRenameOptionClicked(int position);

        void onDeleteOptionClicked(int position);

        void onShowFileInfoOptionClicked(int position);
    }

    public interface OnMultipleItemsLongClickListener {
        void onSelectAllOptionClicked();

        void onDeselectAllOptionClicked(ArrayList<Integer> selectedItemsPositionsList);

        void onShareAllOptionClicked(ArrayList<Integer> selectedItemsPositionsList);

        void onDeleteAllOptionClicked();
    }
}
