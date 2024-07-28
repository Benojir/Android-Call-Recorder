package com.venomdino.callrecorder.adapters;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.venomdino.callrecorder.R;
import com.venomdino.callrecorder.activities.MainActivity;
import com.venomdino.callrecorder.helpers.CustomFunctions;
import com.venomdino.callrecorder.helpers.ViewDialog;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;

public class RVAdapterFileList extends RecyclerView.Adapter<RVAdapterFileList.MyCustomViewHolder> implements Filterable {

    private static final String TAG = MainActivity.TAG;
    Context context;
    JSONArray fileInfos;
    JSONArray fileInfos2;
    boolean isSelectModeOn, isSelectAllOptionClicked;
    ArrayList<Integer> selectedItemsPositionsList = new ArrayList<>();
    ArrayList<Uri> selectedFilesUriList = new ArrayList<>();
    ArrayList<Integer> allPositions;
    ArrayList<Uri> allFilesUriList;


    public RVAdapterFileList(Context context, JSONArray fileInfos, ArrayList<Integer> allPositions, ArrayList<Uri> allFilesUriList){
        this.context = context;
        this.fileInfos = fileInfos;
        fileInfos2 = fileInfos;
        this.allPositions = allPositions;
        this.allFilesUriList = allFilesUriList;
    }

    @NonNull
    @Override
    public MyCustomViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {

        LayoutInflater layoutInflater = LayoutInflater.from(context);
        View view = layoutInflater.inflate(R.layout.sample_rv_layout_main_activity, parent, false);
        return new MyCustomViewHolder(view);
    }

    @SuppressLint({"NotifyDataSetChanged", "UseCompatLoadingForDrawables"})
    @Override
    public void onBindViewHolder(@NonNull MyCustomViewHolder holder, int position) {

        try {
            holder.fileNameTV.setText(fileInfos.getJSONObject(holder.getAdapterPosition()).getString("name"));
            String fileSizeAndDate = fileInfos.getJSONObject(holder.getAdapterPosition()).get("modified_date") + "\t\t (" + fileInfos.getJSONObject(holder.getAdapterPosition()).get("size") + ")";
            holder.fileInfoTV.setText(fileSizeAndDate);
        }
        catch (JSONException e) {
            e.printStackTrace();
        }


//        holder.itemView.setBackgroundColor(context.getColor(R.color.white));

        if (isSelectModeOn){
            MainActivity.menu_selected_items_count.setTitle(selectedItemsPositionsList.size() + "");
        }

        if (selectedItemsPositionsList.contains(holder.getAdapterPosition())){
            holder.itemView.findViewById(R.id.selectionIcon).setVisibility(View.VISIBLE);
        }
        else{
            holder.itemView.findViewById(R.id.selectionIcon).setVisibility(View.GONE);
        }

//        ------------------------------------------------------------------------------------------
        holder.itemView.setOnClickListener(view -> {

            if (isSelectModeOn){

                if (selectedItemsPositionsList.isEmpty()){
                    isSelectModeOn = false;
                    MainActivity.searchBtn.setVisible(true);
                    MainActivity.settingsBtn.setVisible(true);
                    MainActivity.menu_selected_items_count.setVisible(false);
                }
                else{
                    if (selectedItemsPositionsList.contains(holder.getAdapterPosition())){

                        try{
                            selectedItemsPositionsList.remove((Integer) holder.getAdapterPosition()); // eta object er maddhomey remove korchhi
//                            selectedItemsPositionsList.remove(selectedItemsPositionsList.indexOf(holder.getAdapterPosition())); //ota na hole eta index er maddhome remove korchh

                            try {
                                selectedFilesUriList.remove(Uri.fromFile(new File(fileInfos.getJSONObject(holder.getAdapterPosition()).getString("absolute_path"))));
                            }
                            catch (Exception e) {
                                e.printStackTrace();
                                Toast.makeText(context, "" + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                            }

                            holder.itemView.findViewById(R.id.selectionIcon).setVisibility(View.GONE);
                            isSelectAllOptionClicked = false;
                            MainActivity.menu_selected_items_count.setTitle(selectedItemsPositionsList.size() + "");

                        }
                        catch (Exception e){
                            Log.d(TAG, "onBindViewHolder s4: " + e.getMessage());
                        }
                    }
                    else{
                        holder.itemView.findViewById(R.id.selectionIcon).setVisibility(View.VISIBLE);
                        selectedItemsPositionsList.add(holder.getAdapterPosition());
                        MainActivity.menu_selected_items_count.setTitle(selectedItemsPositionsList.size() + "");
                        try {
                            selectedFilesUriList.add(Uri.fromFile(new File(fileInfos.getJSONObject(holder.getAdapterPosition()).getString("absolute_path"))));
                        }
                        catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
            else {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    Uri uri = FileProvider.getUriForFile(context, context.getApplicationContext().getPackageName() + ".provider", new File(fileInfos.getJSONObject(holder.getAdapterPosition()).getString("absolute_path")));
                    intent.setDataAndType(uri, "audio/mpeg");
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    context.startActivity(intent);
                }
                catch (Exception e) {
                    e.printStackTrace();
                    Log.d(TAG, e.getMessage());
                }
            }
        });
//        ------------------------------------------------------------------------------------------
        holder.itemView.setOnLongClickListener(view -> {

            BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(context);
            bottomSheetDialog.setCancelable(true);
            bottomSheetDialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);

            if (isSelectModeOn){
                bottomSheetDialog.setContentView(R.layout.bottom_sheet_dialog_selected_ctrl_btns);

                bottomSheetDialog.findViewById(R.id.selectAllBtnCV).setOnClickListener(view1 -> {
                    isSelectAllOptionClicked = true;
                    selectedItemsPositionsList.clear();
                    selectedItemsPositionsList.addAll(allPositions);
                    selectedFilesUriList.clear();
                    selectedFilesUriList.addAll(allFilesUriList);
                    notifyDataSetChanged();
                    bottomSheetDialog.dismiss();
                });

                bottomSheetDialog.findViewById(R.id.deselectAllBtnCV).setOnClickListener(view1 -> {
                    isSelectAllOptionClicked = false;
                    selectedItemsPositionsList.clear();
                    selectedFilesUriList.clear();
                    isSelectModeOn = false;
                    notifyDataSetChanged();
                    bottomSheetDialog.dismiss();
                    MainActivity.searchBtn.setVisible(true);
                    MainActivity.settingsBtn.setVisible(true);
                    MainActivity.menu_selected_items_count.setVisible(false);
                });

                bottomSheetDialog.findViewById(R.id.shareAllBtnCV).setOnClickListener(view1 -> {
                    Intent intent = new Intent();
                    intent.setAction(Intent.ACTION_SEND_MULTIPLE);
                    intent.putExtra(Intent.EXTRA_SUBJECT, "Here are some files.");
                    intent.setType("audio/mpeg");
                    intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, selectedFilesUriList);
                    context.startActivity(Intent.createChooser(intent, "Share to"));
                    bottomSheetDialog.dismiss();
                });
                
                bottomSheetDialog.findViewById(R.id.deleteAllBtnCV).setOnClickListener(view1 -> {

                    AlertDialog.Builder builder = new AlertDialog.Builder(context);
                    builder.setCancelable(true);
                    builder.setTitle("Delete files");
                    builder.setMessage("Selected files will be deleted.");
                    builder.setIcon(R.drawable.ic_delete_24);
                    builder.setPositiveButton("Delete", (dialogInterface, i) -> {

                        selectedItemsPositionsList.sort(Collections.reverseOrder());

                        int k = 0;

                        while (k < selectedItemsPositionsList.size()) {

                            int filePosition = selectedItemsPositionsList.get(k);

                            try {

                                File temp_file = new File(fileInfos.getJSONObject(filePosition).get("absolute_path").toString());

                                if (temp_file.delete()) {
                                    fileInfos.remove(filePosition);
                                    allPositions.remove(filePosition);
                                    allFilesUriList.remove(Uri.fromFile(new File(fileInfos.getJSONObject(filePosition).get("absolute_path").toString())));
                                    k++;
                                }
                            }
                            catch (Exception e) {
                                e.printStackTrace();
                                Log.d(TAG, "onBindViewHolder error: " + e.getLocalizedMessage());
                                k++;
                            }
                        }

                        Toast.makeText(context, "Selected recordings deleted successfully.", Toast.LENGTH_SHORT).show();

                        isSelectModeOn = false;
                        selectedItemsPositionsList.clear();
                        selectedFilesUriList.clear();
                        notifyDataSetChanged();
                        MainActivity.settingsBtn.setVisible(true);
                        MainActivity.searchBtn.setVisible(true);
                        MainActivity.menu_selected_items_count.setVisible(false);
                        bottomSheetDialog.dismiss();

                        if (fileInfos.length() <= 0){
                            MainActivity.allFilesRecyclerView.setVisibility(View.GONE);
                            MainActivity.emptyFileIconContainer.setVisibility(View.VISIBLE);
                        }
                    });

                    builder.setNegativeButton("Cancel", (dialogInterface, i) -> dialogInterface.dismiss());
                    builder.show();
                });
            }
            else {

                bottomSheetDialog.setContentView(R.layout.bottom_sheet_dialog_control_btns);

                TextView fileNameTVinBottomDialog = bottomSheetDialog.findViewById(R.id.fileNameTVinBottomDialog);
                try {
                    fileNameTVinBottomDialog.setText(fileInfos.getJSONObject(holder.getAdapterPosition()).getString("name"));
                }
                catch (JSONException e) {
                    e.printStackTrace();
                    bottomSheetDialog.findViewById(R.id.fileNameContainerLLinBottomDialog).setVisibility(View.GONE);
                }

                bottomSheetDialog.findViewById(R.id.selectBtnCV).setOnClickListener(view1 -> {
                    isSelectModeOn = true;
                    holder.itemView.findViewById(R.id.selectionIcon).setVisibility(View.VISIBLE);
                    selectedItemsPositionsList.add(holder.getAdapterPosition());
                    try {
                        selectedFilesUriList.add(Uri.fromFile(new File(fileInfos.getJSONObject(holder.getAdapterPosition()).getString("absolute_path"))));
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    bottomSheetDialog.dismiss();
                    MainActivity.searchBtn.setVisible(false);
                    MainActivity.settingsBtn.setVisible(false);
                    MainActivity.menu_selected_items_count.setVisible(true);
                    MainActivity.menu_selected_items_count.setTitle(selectedItemsPositionsList.size() + "");
                });

                bottomSheetDialog.findViewById(R.id.playBtnCV).setOnClickListener(view1 -> {

                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        Uri uri = FileProvider.getUriForFile(context, context.getApplicationContext().getPackageName() + ".provider", new File(fileInfos.getJSONObject(holder.getAdapterPosition()).getString("absolute_path")));
                        intent.setDataAndType(uri, "audio/mpeg");
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        context.startActivity(intent);
                    }
                    catch (Exception e){
                        e.printStackTrace();
                        Log.d(TAG, e.getMessage());
                    }
                });

                bottomSheetDialog.findViewById(R.id.shareRecordingBtnCV).setOnClickListener(view1 -> {

                    try {
                        Intent intent = new Intent(Intent.ACTION_SEND);
                        Uri uri = FileProvider.getUriForFile(context, context.getApplicationContext().getPackageName() + ".provider", new File(fileInfos.getJSONObject(holder.getAdapterPosition()).getString("absolute_path")));
                        intent.setDataAndType(uri, "audio/mpeg");
                        intent.putExtra(Intent.EXTRA_STREAM, uri);
                        context.startActivity(Intent.createChooser(intent, "Share via"));
                    }
                    catch (Exception e){
                        e.printStackTrace();
                        Log.d(TAG, e.getMessage());
                    }
                });

                bottomSheetDialog.findViewById(R.id.deleteBtnCV).setOnClickListener(view1 -> {

                    AlertDialog.Builder builder = new AlertDialog.Builder(context);
                    builder.setCancelable(true);
                    builder.setTitle("Delete");
                    builder.setMessage("Do you really want to delete?");
                    builder.setIcon(R.drawable.ic_delete_24);
                    builder.setPositiveButton("Delete", (dialogInterface, i) -> {
                        try {

                            File temp_file = new File(fileInfos.getJSONObject(holder.getAdapterPosition()).get("absolute_path").toString());

                            if (temp_file.delete()){

                                Toast.makeText(context, "Recorded file deleted successfully.", Toast.LENGTH_SHORT).show();

                                fileInfos.remove(holder.getAdapterPosition());
                                notifyDataSetChanged();
                                notifyItemRemoved(holder.getAdapterPosition());
                                allPositions.remove((Integer) holder.getAdapterPosition());
                                allFilesUriList.remove(Uri.fromFile(new File(fileInfos.getJSONObject(holder.getAdapterPosition()).get("absolute_path").toString())));

                                bottomSheetDialog.dismiss();
                            }
                            else{
                                Toast.makeText(context, "Failed to delete file.", Toast.LENGTH_SHORT).show();
                            }
                        }
                        catch (JSONException e) {
                            e.printStackTrace();
                        }
                    });
                    builder.setNegativeButton("Cancel", (dialogInterface, i) -> dialogInterface.dismiss());
                    builder.show();
                });


                bottomSheetDialog.findViewById(R.id.renameBtnCV).setOnClickListener(view1 -> {

                    try {
                        String oldName = fileInfos.getJSONObject(holder.getAdapterPosition()).get("name").toString();
                        String filePath = fileInfos.getJSONObject(holder.getAdapterPosition()).get("absolute_path").toString();

                        EditText inputField = new EditText(context);
                        inputField.setHint("Enter new file name...");
                        inputField.setText(oldName.substring(0, oldName.length() - 4));
                        inputField.setSingleLine(true);
                        inputField.setInputType(InputType.TYPE_CLASS_TEXT);
                        if (CustomFunctions.isDarkModeOn(context)){
                            inputField.setBackgroundTintList(ColorStateList.valueOf(context.getColor(R.color.hp_theme_color_dark)));
                        }
                        else{
                            inputField.setBackgroundTintList(ColorStateList.valueOf(context.getColor(R.color.hp_theme_color_light)));
                        }
                        inputField.setPadding(
                                context.getResources().getDimensionPixelSize(R.dimen.input_box_padding_lr),
                                context.getResources().getDimensionPixelSize(R.dimen.input_box_padding_tb),
                                context.getResources().getDimensionPixelSize(R.dimen.input_box_padding_lr),
                                context.getResources().getDimensionPixelSize(R.dimen.input_box_padding_tb)
                        );

                        FrameLayout container = new FrameLayout(context);

                        FrameLayout.LayoutParams params = new  FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                        params.leftMargin = context.getResources().getDimensionPixelSize(R.dimen.dialog_margin_left);
                        params.rightMargin = context.getResources().getDimensionPixelSize(R.dimen.dialog_margin_right);
                        params.topMargin = context.getResources().getDimensionPixelSize(R.dimen.dialog_margin_top);

                        inputField.setLayoutParams(params);

                        container.addView(inputField);

                        AlertDialog dialog = new AlertDialog.Builder(context)
                                .setTitle("Rename to:")
                                .setView(container)
                                .setCancelable(false)
                                .setIcon(R.drawable.ic_mode_edit_24)
                                .setPositiveButton("Rename", (dialogInterface, i) -> {

                                    String newName = inputField.getText().toString();

                                    if (!newName.equalsIgnoreCase("")){

                                        newName += ".m4a";

                                        File oldNameFile = new File(filePath);
                                        File newNameFile = new File(oldNameFile.getParent().concat("/" + newName));

                                        if (oldNameFile.renameTo(newNameFile)){

                                            Toast.makeText(context, "File renamed.", Toast.LENGTH_SHORT).show();

                                            JSONObject temp_file_jo = new JSONObject();

                                            try {
                                                temp_file_jo.put("name", newName);
                                                temp_file_jo.put("size", fileInfos.getJSONObject(holder.getAdapterPosition()).get("size").toString());
                                                temp_file_jo.put("modified_date", fileInfos.getJSONObject(holder.getAdapterPosition()).get("modified_date").toString());
                                                temp_file_jo.put("absolute_path", new File(fileInfos.getJSONObject(holder.getAdapterPosition()).get("absolute_path").toString()).getParent().concat("/" + newName));

//                                            fileInfos.remove(holder.getAdapterPosition());
                                                fileInfos.put(holder.getAdapterPosition(), temp_file_jo);

                                                notifyDataSetChanged();
                                            }
                                            catch (JSONException e) {
                                                e.printStackTrace();
                                            }
                                        }
                                        else{
                                            Toast.makeText(context, "Failed", Toast.LENGTH_SHORT).show();
                                        }

                                        dialogInterface.dismiss();
                                    }
                                    else{
                                        Toast.makeText(context, "Please provide a valid name.", Toast.LENGTH_SHORT).show();
                                    }

                                })
                                .setNegativeButton("Cancel", null)
                                .create();

                        dialog.show();
                        bottomSheetDialog.dismiss();
                    }
                    catch (JSONException e) {
                        e.printStackTrace();
                    }
                });


                bottomSheetDialog.findViewById(R.id.infoBtnCV).setOnClickListener(view1 -> {
                    try {
                        new ViewDialog().showFileInfoDialog(context, fileInfos.getJSONObject(holder.getAdapterPosition()));
                        bottomSheetDialog.dismiss();
                    }
                    catch (JSONException e) {
                        e.printStackTrace();
                        Toast.makeText(context, "Something went wrong. Please report this issue.", Toast.LENGTH_SHORT).show();
                    }
                });

            }

            bottomSheetDialog.show();

            return true;
        });
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

            if (charSequence == null || charSequence.length() == 0){
                filteredFileInfos = fileInfos2;
            }
            else{
                String search_term = charSequence.toString().trim().toLowerCase();

                for (int i = 0; i < fileInfos2.length(); i++){

                    try {
                        if (
                                fileInfos2.getJSONObject(i).getString("name").toLowerCase().contains(search_term)
                                ||
                                fileInfos2.getJSONObject(i).getString("modified_date").toLowerCase().contains(search_term)
                        ){
                            filteredFileInfos.put(fileInfos2.getJSONObject(i));
                        }
                    }
                    catch (JSONException e) {
                        e.printStackTrace();
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

    public static class MyCustomViewHolder extends RecyclerView.ViewHolder{

        TextView fileNameTV, fileInfoTV;

        public MyCustomViewHolder(View itemView) {
            super(itemView);

            fileNameTV = itemView.findViewById(R.id.fileNameTV);
            fileInfoTV = itemView.findViewById(R.id.fileInfoTV);
        }
    }
}
