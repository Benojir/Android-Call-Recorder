package zoro.benojir.callrecorder.dialogs;

import android.app.Activity;
import android.app.Dialog;
import android.net.Uri;
import android.util.Log;
import android.view.MotionEvent;
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

import java.io.File;

import zoro.benojir.callrecorder.R;

@OptIn(markerClass = UnstableApi.class)
public class AudioPlayerDialog {

    private static final String TAG = "MADARA";
    private final Dialog dialog;
    private final TextView fileNameTV;
    private final ImageButton backButton, skipBackward, skipForward;
    private ExoPlayer exoPlayer;
    private final PlayerView playerView;
    private String fileName = "Unknown";

    public AudioPlayerDialog(Activity activity, File file) {

        dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);
        dialog.setContentView(R.layout.dialog_audio_player);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT);
            window.setBackgroundDrawableResource(android.R.color.black);

            // Remove dim behind the dialog
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }

        fileNameTV = dialog.findViewById(R.id.file_name_tv);

        playerView = dialog.findViewById(R.id.exo_player_view);
        backButton = dialog.findViewById(R.id.back_button);
        skipBackward = dialog.findViewById(R.id.backward_skip);
        skipForward = dialog.findViewById(R.id.forward_skip);

        try {
            fileName = file.getName();
            exoPlayer = new ExoPlayer.Builder(activity).build();
            exoPlayer.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)));
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

        dialog.setOnCancelListener(dialog -> {
            if (exoPlayer != null) {
                exoPlayer.stop();
                exoPlayer.release();
            }
        });

        playerView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                return true; // Indicate the event was handled
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                v.performClick(); // Call performClick when ACTION_UP is detected
                return true; // Indicate the event was handled
            }
            return false;
        });

        playerView.setOnClickListener(v -> playerView.showController());
    }

    private void actionsOnDialog() {

        backButton.setOnClickListener(view -> {
            if (exoPlayer != null) {
                exoPlayer.stop();
                exoPlayer.release();
            }

            dialog.dismiss();
        });

        skipBackward.setOnClickListener(view -> exoPlayer.seekTo(exoPlayer.getCurrentPosition() - 5000));

        skipForward.setOnClickListener(view -> exoPlayer.seekTo(exoPlayer.getCurrentPosition() + 5000));
    }
}
