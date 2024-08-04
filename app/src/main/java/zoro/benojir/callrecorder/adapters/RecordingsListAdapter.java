package zoro.benojir.callrecorder.adapters;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
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

import zoro.benojir.callrecorder.R;
import zoro.benojir.callrecorder.dialogs.FileInfoDialog;
import zoro.benojir.callrecorder.dialogs.SingleFileControlDialog;
import zoro.benojir.callrecorder.helpers.RecordingFilesOptionsHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;

public class RecordingsListAdapter extends RecyclerView.Adapter<RecordingsListAdapter.MyCustomViewHolder> implements Filterable {

    private static final String TAG = "MADARA";
    private final Activity activity;
    private JSONArray fileInfos;
    private final JSONArray fileInfos2;
    private boolean isSelectionModeOn;
    private final ArrayList<Integer> selectedItemsPositionsList = new ArrayList<>();

    public RecordingsListAdapter(Activity activity, JSONArray fileInfos) {
        this.activity = activity;
        this.fileInfos = fileInfos;
        this.fileInfos2 = fileInfos;
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
            String fileName = fileInfos.getJSONObject(holder.getAdapterPosition()).getString("file_name");
            String modifiedDate = fileInfos.getJSONObject(holder.getAdapterPosition()).getString("modified_date");
            String fileSize = fileInfos.getJSONObject(holder.getAdapterPosition()).getString("size");

            String fileSizeAndDate = modifiedDate + "\t\t(" + fileSize + ")";

            holder.fileNameTV.setText(fileName);
            holder.fileInfoTV.setText(fileSizeAndDate);

            File file = new File(fileInfos.getJSONObject(holder.getAdapterPosition()).getString("absolute_path"));
            RecordingFilesOptionsHelper fileOptionsHelper = new RecordingFilesOptionsHelper(activity, file);

            //................................................................

            holder.itemView.setOnClickListener(view -> {
                if (isSelectionModeOn) {
                    if (selectedItemsPositionsList.contains(holder.getAdapterPosition())) {
                        removeSelection(holder);
                        if (selectedItemsPositionsList.isEmpty()) {
                            isSelectionModeOn = false;
                        }
                    } else {
                        setSelection(holder);
                    }
                } else {
                    fileOptionsHelper.playRecording();
                }
            });

            //................................................................

            holder.itemView.setOnLongClickListener(view -> {
                try{
                    if (isSelectionModeOn) {
                        Toast.makeText(activity, "Selection mode is already on.", Toast.LENGTH_SHORT).show();
                    } else {

                        JSONObject fileInfo = fileInfos.getJSONObject(holder.getAdapterPosition());
                        SingleFileControlDialog singleFileControlDialog = new SingleFileControlDialog(activity, fileInfo, holder.getAdapterPosition());

                        singleFileControlDialog.show(new OnSingleItemLongClickListener() {
                            @Override
                            public void onSelectOptionClicked(int position) {
                                isSelectionModeOn = true;
                                setSelection(holder);
                            }

                            @Override
                            public void onPlayOptionClicked(int position) {
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
                                        fileInfos.put(position, fileInfoJObject);
                                        notifyItemChanged(position);
                                    } else {
                                        Toast.makeText(activity, "Failed to rename file.", Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }

                            @Override
                            public void onDeleteOptionClicked(int position) {
                                fileOptionsHelper.deleteFile(success -> {
                                    if (success) {
                                        fileInfos.remove(position);
                                        notifyItemRemoved(position);
                                        selectedItemsPositionsList.remove((Integer) position);
                                        isSelectionModeOn = false;
                                    } else {
                                        Toast.makeText(activity, "Failed to delete file.", Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }

                            @Override
                            public void onShowFileInfoOptionClicked(int position) {
                                new FileInfoDialog(activity, fileInfo).show();
                            }
                        });
                    }
                } catch (Exception e){
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

    @Override
    public Filter getFilter() {
        return filesFilter;
    }

    private final Filter filesFilter = new Filter() {
        @Override
        protected FilterResults performFiltering(CharSequence charSequence) {

            JSONArray filteredFileInfos = new JSONArray();

            if (charSequence == null || charSequence.length() == 0) {
                filteredFileInfos = fileInfos2;
            } else {
                String search_term = charSequence.toString().trim().toLowerCase();

                for (int i = 0; i < fileInfos2.length(); i++) {

                    try {
                        boolean doesNameContainSearchTerm = fileInfos2.getJSONObject(i).getString("name").toLowerCase().contains(search_term);
                        boolean doesModifiedDateContainSearchTerm = fileInfos2.getJSONObject(i).getString("modified_date").toLowerCase().contains(search_term);

                        if (doesNameContainSearchTerm || doesModifiedDateContainSearchTerm) {
                            filteredFileInfos.put(fileInfos2.getJSONObject(i));
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "onBindViewHolder: ", e);
                    }
                }
            }

            FilterResults filterResults = new FilterResults();
            filterResults.values = filteredFileInfos;
            filterResults.count = filteredFileInfos.length();

            return filterResults;
        }

        @SuppressLint("NotifyDataSetChanged")
        @Override
        protected void publishResults(CharSequence charSequence, FilterResults filterResults) {
            fileInfos = (JSONArray) filterResults.values;
            notifyDataSetChanged();
        }
    };

    public static class MyCustomViewHolder extends RecyclerView.ViewHolder {

        private final RelativeLayout mainLayout;
        private final TextView fileNameTV;
        private final TextView fileInfoTV;
        private final ImageView selectionIcon;

        public MyCustomViewHolder(View itemView) {
            super(itemView);
            mainLayout = itemView.findViewById(R.id.mainLayout);
            fileNameTV = itemView.findViewById(R.id.fileNameTV);
            fileInfoTV = itemView.findViewById(R.id.fileInfoTV);
            selectionIcon = itemView.findViewById(R.id.selectionIcon);
        }
    }

    private void setSelection(MyCustomViewHolder holder) {
        holder.mainLayout.setBackgroundColor(activity.getColor(R.color.fade_blue));
        holder.selectionIcon.setVisibility(View.VISIBLE);
        selectedItemsPositionsList.add(holder.getAdapterPosition());
    }

    private void removeSelection(MyCustomViewHolder holder) {
        holder.mainLayout.setBackgroundColor(Color.TRANSPARENT);
        holder.selectionIcon.setVisibility(View.GONE);
        selectedItemsPositionsList.remove((Integer) holder.getAdapterPosition());
    }

//    ----------------------------------------------------------------------------------------------

    public interface OnSingleItemLongClickListener {
        void onSelectOptionClicked(int position);
        void onPlayOptionClicked(int position);
        void onShareOptionClicked(int position);
        void onRenameOptionClicked(int position);
        void onDeleteOptionClicked(int position);
        void onShowFileInfoOptionClicked(int position);
    }
}
