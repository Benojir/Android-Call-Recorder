package zoro.benojir.callrecorder.dialogs;

import android.app.Activity;
import android.app.Dialog;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MenuInflater;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.widget.PopupMenu;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;

import java.io.File;

import zoro.benojir.callrecorder.R;
import zoro.benojir.callrecorder.helpers.CustomFunctions;

@OptIn(markerClass = UnstableApi.class)
public class AudioPlayerDialog {

    private static final String TAG = "MADARA";
    private final Activity activity;
    private final File file;
    private final Dialog dialog;
    private final TextView durationTV;
    private final TextView speedOptionSelectorTV;
    private final SeekBar seekBar;
    private final ImageButton playOrPause, skipBackward, skipForward;
    private ExoPlayer exoPlayer;
    private String totalAudioDuration = "00:00:00";
    private boolean isUserSeeking;
    private boolean playingFinished;

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
        this.activity = activity;
        this.file = file;
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

        initializeExoPlayer();

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

                if (progress < seekBar.getMax()){
                    playingFinished = false;
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isUserSeeking = true;

                if (playingFinished) {
                    exoPlayer.pause();
                    playOrPause.setImageResource(R.drawable.play_arrow_24);
                }
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                isUserSeeking = false;
                long shouldSeekTo = seekBar.getProgress() * 1000L;
                exoPlayer.seekTo(shouldSeekTo);

                String formatted = CustomFunctions.formatDuration(shouldSeekTo) + " · " + totalAudioDuration;
                durationTV.setText(formatted);
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


        actionsOnDialog();
        dialog.show();
    }

    //    ------------------------------------------------------------------------------------------
    private void actionsOnDialog() {

        playOrPause.setOnClickListener(view -> {
            if (exoPlayer != null) {

                if (playingFinished) {
                    playingFinished = false;
                    exoPlayer.seekTo(0);
                    exoPlayer.play();
                    seekBar.setProgress(0);
                    playOrPause.setImageResource(R.drawable.pause_24);
                } else{
                    if (exoPlayer.isPlaying()) {
                        exoPlayer.pause();
                        playOrPause.setImageResource(R.drawable.play_arrow_24);
                    } else {
                        exoPlayer.play();
                        playOrPause.setImageResource(R.drawable.pause_24);
                    }
                }
            }
        });


        skipBackward.setOnClickListener(view -> {
            // If the playback had finished, ensure the player remains paused
            if (playingFinished) {
                exoPlayer.pause();
                playOrPause.setImageResource(R.drawable.play_arrow_24);
            }

            long currentPosition = exoPlayer.getCurrentPosition();
            long newPosition = Math.max(currentPosition - 5000, 0);
            exoPlayer.seekTo(newPosition);

            // Update SeekBar and TextView
            seekBar.setProgress((int) (newPosition / 1000));
            String formatted = CustomFunctions.formatDuration(newPosition) + " · " + totalAudioDuration;
            durationTV.setText(formatted);
            updateDurationTextView(newPosition);
        });


        skipForward.setOnClickListener(view -> {
            // If the playback had finished, ensure the player remains paused
            if (playingFinished) {
                exoPlayer.pause();
                playOrPause.setImageResource(R.drawable.play_arrow_24);
            }

            long currentPosition = exoPlayer.getCurrentPosition();
            long newPosition = Math.min(currentPosition + 5000, exoPlayer.getDuration());
            exoPlayer.seekTo(newPosition);

            // Update SeekBar and TextView
            seekBar.setProgress((int) (newPosition / 1000));
            updateDurationTextView(newPosition);
        });


        speedOptionSelectorTV.setOnClickListener(view -> {
            PopupMenu speedMenu = new PopupMenu(view.getContext(), view);
            MenuInflater inflater = speedMenu.getMenuInflater();
            inflater.inflate(R.menu.speed_menu, speedMenu.getMenu());

            speedMenu.setOnMenuItemClickListener(item -> {

                float playbackSpeed;

                if (item.getItemId() == R.id.speed_0_75) {
                    playbackSpeed = 0.75f;
                } else if (item.getItemId() == R.id.speed_1_0) {
                    playbackSpeed = 1.0f;
                } else if (item.getItemId() == R.id.speed_1_25) {
                    playbackSpeed = 1.25f;
                } else if (item.getItemId() == R.id.speed_1_5) {
                    playbackSpeed = 1.5f;
                } else if (item.getItemId() == R.id.speed_2_0) {
                    playbackSpeed = 2.0f;
                } else {
                    playbackSpeed = 1.0f; // Default speed
                }

                // Set the playback speed for ExoPlayer
                if (exoPlayer != null) {
                    exoPlayer.setPlaybackParameters(new PlaybackParameters(playbackSpeed));
                }

                // Update the TextView to reflect the selected speed
                String speedText = item.getTitle().toString();
                speedOptionSelectorTV.setText(speedText);

                return true;
            });

            // Show the PopupMenu
            speedMenu.show();
        });
    }

    //    ------------------------------------------------------------------------------------------

    private void initializeExoPlayer() {

        exoPlayer = new ExoPlayer.Builder(activity).build();
        exoPlayer.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)));
        exoPlayer.prepare();
        exoPlayer.setPlayWhenReady(true);

        playOrPause.setImageResource(R.drawable.pause_24);

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

                if (playbackState == Player.STATE_READY) { // calling everytime when the player is ready even after the user is seeking or buffering but it is not onPlaying
                    long audioDuration = exoPlayer.getDuration();
                    int durationInSecs = (int) (audioDuration / 1000); // Convert to seconds
                    totalAudioDuration = CustomFunctions.formatDuration(audioDuration);
                    seekBar.setMax(durationInSecs);
                }
                if (playbackState == Player.STATE_ENDED) {
                    playOrPause.setImageResource(R.drawable.play_arrow_24);

                    String finishedPlayingDuration = totalAudioDuration + " · " + totalAudioDuration;
                    durationTV.setText(finishedPlayingDuration);

                    seekBar.setProgress(seekBar.getMax());

                    playingFinished = true;
                }
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

    private void updateDurationTextView(long position) {
        String formatted = CustomFunctions.formatDuration(position) + " · " + totalAudioDuration;
        durationTV.setText(formatted);
    }
}
