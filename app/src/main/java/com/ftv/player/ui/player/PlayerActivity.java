package com.ftv.player.ui.player;

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.ftv.player.R;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;
import com.google.android.exoplayer2.ui.StyledPlayerView;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;

public class PlayerActivity extends AppCompatActivity {

    private StyledPlayerView playerView;
    private ExoPlayer player;
    private TextView channelNameText;
    private String channelUrl;
    private String channelName;
    private boolean isPlaying = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        channelUrl = getIntent().getStringExtra("channel_url");
        channelName = getIntent().getStringExtra("channel_name");

        channelNameText = findViewById(R.id.channel_name);
        playerView = findViewById(R.id.player_view);

        if (channelName != null) {
            channelNameText.setText(channelName);
        }

        if (channelUrl == null || channelUrl.isEmpty()) {
            Toast.makeText(this, "No stream URL available", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        setupPlayer();
    }

    private void setupPlayer() {
        player = new ExoPlayer.Builder(this)
                .setSeekForwardIncrementMs(30000)
                .setSeekBackIncrementMs(10000)
                .build();

        playerView.setPlayer(player);
        playerView.setShowBuffering(StyledPlayerView.SHOW_BUFFERING_ALWAYS);
        playerView.setControllerShowTimeoutMs(3000);
        playerView.setControllerAutoShow(true);
        playerView.setUseController(true);
        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);

        DefaultHttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setUserAgent("FTVPlayer/1.0 (Android TV)")
                .setConnectTimeoutMs(8000)
                .setReadTimeoutMs(15000);

        Uri uri = Uri.parse(channelUrl);
        MediaItem mediaItem = MediaItem.fromUri(uri);

        if (channelUrl.contains(".m3u8")) {
            HlsMediaSource hlsSource = new HlsMediaSource.Factory(httpFactory)
                    .setAllowChunklessPreparation(true)
                    .createMediaSource(mediaItem);
            player.setMediaSource(hlsSource);
        } else {
            player.setMediaItem(mediaItem);
        }

        player.setPlayWhenReady(true);
        player.prepare();

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                switch (state) {
                    case Player.STATE_BUFFERING:
                        channelNameText.setVisibility(View.VISIBLE);
                        break;
                    case Player.STATE_READY:
                        channelNameText.setVisibility(View.GONE);
                        break;
                    case Player.STATE_ENDED:
                        player.seekTo(0);
                        break;
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                Toast.makeText(PlayerActivity.this,
                        "Playback error: " + error.getLocalizedMessage(),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (playerView != null && playerView.getControllerShowTimeoutMs() > 0) {
            playerView.showController();
        }

        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (player != null) {
                    if (player.isPlaying()) {
                        player.pause();
                    } else {
                        player.play();
                    }
                    isPlaying = player.isPlaying();
                }
                return true;

            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                if (player != null) {
                    if (player.isPlaying()) {
                        player.pause();
                    } else {
                        player.play();
                    }
                }
                return true;

            case KeyEvent.KEYCODE_MEDIA_STOP:
                if (player != null) player.stop();
                finish();
                return true;

            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                if (player != null) player.seekTo(player.getCurrentPosition() - 10000);
                return true;

            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                if (player != null) player.seekTo(player.getCurrentPosition() + 30000);
                return true;

            case KeyEvent.KEYCODE_MEDIA_NEXT:
                finish();
                return true;

            case KeyEvent.KEYCODE_BACK:
                finish();
                return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (player != null) {
            player.setPlayWhenReady(false);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (player != null) {
            player.setPlayWhenReady(true);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (player != null) {
            player.release();
            player = null;
        }
    }
}
