package com.example.audioplayer;

import android.Manifest;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private SongAdapter adapter;
    private MediaBrowserCompat mediaBrowser;
    private MediaControllerCompat mediaController;
    private ImageButton btnNext, btnPrevious, btnPlay, btnPause;
    private TextView currentPlayTime, totalPlayTime, audioTitle;
    private ImageView albumArt;
    private SeekBar seekBar;
    private Runnable updateSeekbar;
    private Handler handler = new Handler();

    private static final int PERMISSION_REQUEST_CODE = 123;

    private final MediaBrowserCompat.ConnectionCallback connectionCallBacks =
            new MediaBrowserCompat.ConnectionCallback() {
                @Override
                public void onConnected() {
                    // Connection successful, getting session token from service
                    mediaController = new MediaControllerCompat(MainActivity.this, mediaBrowser.getSessionToken());

                    // Registering the Controller with Activity
                    MediaControllerCompat.setMediaController(MainActivity.this, mediaController);

                    mediaController.registerCallback(controllerCallback);
                    // Add this right after you register the controllerCallback:
                    mediaBrowser.subscribe(mediaBrowser.getRoot(), subscriptionCallback);

                    controllerCallback.onMetadataChanged(mediaController.getMetadata());
                    controllerCallback.onPlaybackStateChanged(mediaController.getPlaybackState());
                }

            };

    private final MediaControllerCompat.Callback controllerCallback =
            new MediaControllerCompat.Callback() {
                @Override
                public void onMetadataChanged(MediaMetadataCompat metadata) {
                    if (metadata == null) return;

                    audioTitle.setText(metadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE));

                    Bitmap art = metadata.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART);
                    if (art != null) {
                        albumArt.setImageBitmap(art);
                    } else {
                        albumArt.setImageDrawable(null);
                    }

                    long duration = metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION);
                    seekBar.setMax((int) duration);
                    totalPlayTime.setText(formatTime((int) duration));
                }

                @Override
                public void onPlaybackStateChanged(PlaybackStateCompat state){
                    if(state == null) return;

                    seekBar.setProgress((int) state.getPosition());
                    currentPlayTime.setText(formatTime((int) state.getPosition()));
                }

            };

    private final MediaBrowserCompat.SubscriptionCallback subscriptionCallback =
            new MediaBrowserCompat.SubscriptionCallback() {
                @Override
                public void onChildrenLoaded(String parentId, List<MediaBrowserCompat.MediaItem> children) {
                    // We got the list from AudioPlayerService! Send it to the RecyclerView.
                    adapter.submitList(children);
                }
            };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnPlay = findViewById(R.id.btnPlay);
        btnPause = findViewById(R.id.btnPause);
        btnNext = findViewById(R.id.btnNext);
        btnPrevious = findViewById(R.id.btnPrevious);
        seekBar = findViewById(R.id.seekBar);
        currentPlayTime = findViewById(R.id.tvCurrentTime);
        totalPlayTime = findViewById(R.id.tvTotalTime);
        albumArt = findViewById(R.id.ivAlbumArt);
        audioTitle = findViewById(R.id.tvSongTitle);

        mediaBrowser = new MediaBrowserCompat(this,
                new ComponentName(this, AudioPlayerService.class),
                connectionCallBacks,
                null);

        // Background task to update seek bar
        updateSeekbar = new Runnable() {
            @Override
            public void run() {
                if (mediaController != null && mediaController.getPlaybackState() != null) {

                    // Only update the UI if the background engine is currently PLAYING
                    if (mediaController.getPlaybackState().getState() == PlaybackStateCompat.STATE_PLAYING) {

                        // Ask the universal remote for the current exact timestamp
                        long currentPosition = mediaController.getPlaybackState().getPosition();

                        seekBar.setProgress((int) currentPosition);
                        currentPlayTime.setText(formatTime((int) currentPosition));
                    }
                }
                // Run this loop again in 1 second
                handler.postDelayed(this, 1000);
            }
        };

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && mediaController != null) {
                    mediaController.getTransportControls().seekTo(progress);
                }
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Play Button logic
        btnPlay.setOnClickListener(v -> {
            if (mediaController != null) {
                mediaController.getTransportControls().play();
            }
        });

        // Pause Button logic
        btnPause.setOnClickListener(v -> {
            if(mediaController != null){
                mediaController.getTransportControls().pause();
            }
        });

        // Previous button logic
        btnPrevious.setOnClickListener(v -> {
            if (mediaController != null) {
                mediaController.getTransportControls().skipToPrevious();
            }
        });

        // Next button
        btnNext.setOnClickListener(v -> {
            if (mediaController != null) {
                mediaController.getTransportControls().skipToNext();
            }
        });

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

// When a song is clicked, send the Play command to the Service using its Media ID
        adapter = new SongAdapter(item -> {
            if (mediaController != null) {
                mediaController.getTransportControls().playFromMediaId(item.getMediaId(), null);
            }
        });
        recyclerView.setAdapter(adapter);

        requestStoragePermission();
        handler.post(updateSeekbar);
    }

    @Override
    public void onStart() {
        super.onStart();

        if (!mediaBrowser.isConnected()) {
            mediaBrowser.connect();
        }
    }

    private void requestStoragePermission() {
        String permission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? Manifest.permission.READ_MEDIA_AUDIO
                : Manifest.permission.READ_EXTERNAL_STORAGE;

        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{permission}, PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE && grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {

            Toast.makeText(this, "Permission granted. Press Play!", Toast.LENGTH_SHORT).show();

        } else {
            Toast.makeText(this, "Permission denied, cannot load songs", Toast.LENGTH_LONG).show();
        }
    }


    @Override
    public void onStop() {
        super.onStop();

        if (mediaBrowser.isConnected()) {
            mediaBrowser.disconnect();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(updateSeekbar);

    }

    private String formatTime(int milliseconds) {
        int seconds = (milliseconds / 1000) % 60;
        int minutes = (milliseconds / (1000 * 60)) % 60;
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }
}