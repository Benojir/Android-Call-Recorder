package zoro.benojir.callrecorder.dialogs;

import android.app.Activity;
import android.app.Dialog;
import android.net.Uri;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.OptIn;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;

import zoro.benojir.callrecorder.R;
import zoro.benojir.callrecorder.adapters.RecordingsListAdapter;

public class AudioPlayerDialog {

    private static final String TAG = "MADARA";
    private final JSONObject fileInfo;
    private final Dialog dialog;
    private final TextView fileNameTV;
    private final PlayerView playerView;
    private final ImageButton backButton, skipBackward, skipForward;
    private ExoPlayer exoPlayer;
    private String fileName = "Unknown";


    @OptIn(markerClass = UnstableApi.class)
    public AudioPlayerDialog(Activity activity, JSONObject fileInfo) {
        this.fileInfo = fileInfo;

        dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);
        dialog.setContentView(R.layout.dialog_audio_player);
        dialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);

        fileNameTV = dialog.findViewById(R.id.file_name_tv);

        playerView = dialog.findViewById(R.id.exo_player_view);
        backButton = dialog.findViewById(R.id.back_button);
        skipBackward = dialog.findViewById(R.id.backward_skip);
        skipForward = dialog.findViewById(R.id.forward_skip);



        try {
            fileName = fileInfo.getString("file_name");
            exoPlayer = new ExoPlayer.Builder(activity).build();
            exoPlayer.setMediaItem(MediaItem.fromUri(Uri.fromFile(new File(fileInfo.getString("absolute_path")))));
            exoPlayer.prepare();
            exoPlayer.setPlayWhenReady(true);

            playerView.setPlayer(exoPlayer);
            playerView.setShowNextButton(false);
            playerView.setShowPreviousButton(false);
            playerView.setControllerShowTimeoutMs(0); //always show controller
            playerView.showController();

        } catch (Exception e) {
            Toast.makeText(activity, "Cannot play this recording.", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            Log.e(TAG, "AudioPlayerDialog: ", e);
        }
    }

    public void show() {
        fileNameTV.setText(fileName);
        actionsOnDialog();
        dialog.show();
    }

    private void actionsOnDialog() {

        backButton.setOnClickListener(view -> {
            if (exoPlayer != null) {
                exoPlayer.stop();
                exoPlayer.release();
            }

            dialog.dismiss();
        });

        playOption.setOnClickListener(view -> {
            singleItemLongClickListener.onPlayOptionClicked(itemPosition);
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
