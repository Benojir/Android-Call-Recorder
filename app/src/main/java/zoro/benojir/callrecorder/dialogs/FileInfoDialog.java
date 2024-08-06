package zoro.benojir.callrecorder.dialogs;

import android.app.Dialog;
import android.content.Context;
import android.net.Uri;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

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

            // Use ExoPlayer to get the duration
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
        ExoPlayer exoPlayer = new ExoPlayer.Builder(context).build();
        MediaItem mediaItem = MediaItem.fromUri(Uri.fromFile(audioFile));
        exoPlayer.setMediaItem(mediaItem);
        exoPlayer.prepare();

        exoPlayer.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_READY) {
                    long duration = exoPlayer.getDuration();
                    fileDurationTV.setText(CustomFunctions.formatDuration(duration));
                    exoPlayer.release();
                } else if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) {
                    fileDurationTV.setText(context.getString(R.string.unknown));
                    exoPlayer.release();
                }
            }

            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                fileDurationTV.setText(context.getString(R.string.unknown));
                exoPlayer.release();
            }
        });
    }
}
