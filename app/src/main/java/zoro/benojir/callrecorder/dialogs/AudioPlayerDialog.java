package zoro.benojir.callrecorder.dialogs;

import android.app.Activity;
import android.app.Dialog;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Timeline;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;

import java.io.File;

import zoro.benojir.callrecorder.R;
import zoro.benojir.callrecorder.helpers.CustomFunctions;

@OptIn(markerClass = UnstableApi.class)
public class AudioPlayerDialog {

    private static final String TAG = "MADARA";
    private final Dialog dialog;
    private final TextView durationTV;
    private final TextView speedOptionSelectorTV;
    private final SeekBar seekBar;
    private final ImageButton playOrPause, skipBackward, skipForward;
    private ExoPlayer exoPlayer;
    private String totalAudioDuration = "00:00:00";
    private boolean isUserSeeking;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable updateSeekBar = new Runnable() {
        @Override
        public void run() {
            if (!isUserSeeking && exoPlayer.isPlaying()) {
                seekBar.setProgress((int) exoPlayer.getCurrentPosition());
                updateUIWhilePlaying(exoPlayer.getCurrentPosition());
            }
            handler.postDelayed(this, 1000);
        }
    };

    //    ------------------------------------------------------------------------------------------
    public AudioPlayerDialog(Activity activity, File file) {

        dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);
        dialog.setContentView(R.layout.dialog_audio_player);
        dialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);

        TextView fileNameTV = dialog.findViewById(R.id.fileNameTV);
        durationTV = dialog.findViewById(R.id.duration_timer);
        speedOptionSelectorTV = dialog.findViewById(R.id.speed_option_selector);

        seekBar = dialog.findViewById(R.id.seekBar);

        playOrPause = dialog.findViewById(R.id.playOrPause);
        skipBackward = dialog.findViewById(R.id.skipBackward);
        skipForward = dialog.findViewById(R.id.skipForward);

        String fileName = file.getName();
        fileNameTV.setText(fileName);

        try {
            exoPlayer = new ExoPlayer.Builder(activity).build();
            exoPlayer.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)));
            exoPlayer.prepare();
            exoPlayer.setPlayWhenReady(true);

            exoPlayer.addListener(new Player.Listener() {
                @Override
                public void onPlayerError(@NonNull PlaybackException error) {
                    Player.Listener.super.onPlayerError(error);
                    Toast.makeText(activity, "Cannot play this recording.", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    Log.e(TAG, "onPlayerError: ", error);
                }

                @Override
                public void onIsPlayingChanged(boolean isPlaying) {
                    if (isPlaying) {
                        startSeekBarUpdate();
                    } else {
                        stopSeekBarUpdate();
                    }
                }

                @Override
                public void onPlaybackStateChanged(int playbackState) {

                    if (playbackState == Player.STATE_READY) {
                        long audioDuration = exoPlayer.getDuration();
                        int durationInSecs = (int) (audioDuration / 1000); // Convert to seconds
                        totalAudioDuration = CustomFunctions.formatDuration(audioDuration);
                        String formatted = "00:00:00 · " + totalAudioDuration;
                        durationTV.setText(formatted);
                        seekBar.setMax(durationInSecs);
                    }
                }

                @Override
                public void onTimelineChanged(@NonNull Timeline timeline, int reason) {
                    Player.Listener.super.onTimelineChanged(timeline, reason);

                    Log.d(TAG, "onTimelineChanged: " + exoPlayer.getCurrentPosition());
                }
            });

            //--------------------------------------------------------------------------------------

            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) {
                        long shouldSeekTo = progress * 1000L;
                        exoPlayer.seekTo(shouldSeekTo);

                        String formatted = CustomFunctions.formatDuration(shouldSeekTo) + " · " + totalAudioDuration;
                        durationTV.setText(formatted);
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                    isUserSeeking = true;
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    isUserSeeking = false;
                    long shouldSeekTo = seekBar.getProgress() * 1000L;
                    exoPlayer.seekTo(shouldSeekTo);

                    String formatted = CustomFunctions.formatDuration(shouldSeekTo) + " · " + totalAudioDuration;
                    durationTV.setText(formatted);

                    if (!exoPlayer.isPlaying()){
                        exoPlayer.play();
                        playOrPause.setImageResource(R.drawable.pause_24);
                    }
                }
            });

            //--------------------------------------------------------------------------------------

            dialog.setOnCancelListener(dialog -> {
                if (exoPlayer != null) {
                    exoPlayer.stop();
                    exoPlayer.release();
                    handler.removeCallbacks(updateSeekBar);
                }
            });

        } catch (Exception e) {
            Toast.makeText(activity, "Cannot play this recording.", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            Log.e(TAG, "AudioPlayerDialog: ", e);
        }

        actionsOnDialog();
        dialog.show();
    }

    //    ------------------------------------------------------------------------------------------
    private void actionsOnDialog() {

        playOrPause.setOnClickListener(view -> {
            if (exoPlayer != null) {
                if (exoPlayer.isPlaying()) {
                    exoPlayer.pause();
                    playOrPause.setImageResource(R.drawable.play_arrow_24);
                } else {
                    exoPlayer.play();
                    playOrPause.setImageResource(R.drawable.pause_24);
                }
            }
        });

        skipBackward.setOnClickListener(view -> {
            long currentPosition = exoPlayer.getCurrentPosition();
            exoPlayer.seekTo(currentPosition - 5000);

            if (currentPosition - 5000 < 0) {
                seekBar.setProgress(0);
            }
        });

        skipForward.setOnClickListener(view -> {
            long currentPosition = exoPlayer.getCurrentPosition();
            exoPlayer.seekTo(currentPosition + 5000);

            if (currentPosition + 5000 > exoPlayer.getDuration()) {
                seekBar.setProgress((int) exoPlayer.getDuration());
            }
        });


    }

    //    ------------------------------------------------------------------------------------------

    private void startSeekBarUpdate() {
        handler.post(updateSeekBar);
    }

    private void stopSeekBarUpdate() {
        handler.removeCallbacks(updateSeekBar);
    }

    private void updateUIWhilePlaying(long currentPosition) {
        int currentProgressInSecs = (int) (currentPosition / 1000);
        String formatted = CustomFunctions.formatDuration(currentPosition) + " · " + totalAudioDuration;
        durationTV.setText(formatted);
        seekBar.setProgress(currentProgressInSecs);
    }
}
