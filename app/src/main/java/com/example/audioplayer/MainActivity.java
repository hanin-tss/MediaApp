package com.example.audioplayer;

import android.Manifest;
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
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private MediaPlayer mediaPlayer;
    private ImageButton btnNext, btnPrevious, btnPlay, btnPause;
    private TextView currentPlayTime, totalPlayTime, audioTitle;
    private ImageView albumArt;
    private SeekBar seekBar;
    private Handler handler = new Handler();
    private Runnable updateSeekbar;
    private ArrayList playList = new ArrayList<>();
    private int currentIndex = 0;
    private static final int PERMISSION_REQUEST_CODE = 123;

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

        // Background task to update seek bar
        updateSeekbar = new Runnable() {
            @Override
            public void run() {
                if(mediaPlayer != null && mediaPlayer.isPlaying()){
                    seekBar.setProgress(mediaPlayer.getCurrentPosition());

                    // get current played time
                    int timeInMilli = mediaPlayer.getCurrentPosition();
                    int minutes = (timeInMilli / 1000) / 60;
                    int seconds = (timeInMilli / 1000) % 60;
                    String playedTime = String.format("%02d:%02d", minutes, seconds);
                    currentPlayTime.setText(playedTime);
                }

                handler.postDelayed(this, 1000);
            }
        };

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && mediaPlayer != null) {
                    mediaPlayer.seekTo(progress); // Jump to the dragged position
                }
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                handler.removeCallbacks(updateSeekbar);
            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                handler.postDelayed(updateSeekbar, 0);
            }
        });

        // Play Button logic
        btnPlay.setOnClickListener(v -> {
            if (mediaPlayer == null) {
                playSong(currentIndex);
            }

            if (!mediaPlayer.isPlaying()) {
                mediaPlayer.start();
                handler.postDelayed(updateSeekbar, 1000);
            }
        });

        // Pause Button logic
        btnPause.setOnClickListener(v -> {
            if (mediaPlayer != null) {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.pause();
                }
            }
        });

        // Previous button logic
        btnPrevious.setOnClickListener(v -> {
            currentIndex = (currentIndex - 1 + playList.size()) % playList.size();
            playSong(currentIndex);
        });

        // Next button
        btnNext.setOnClickListener(v -> {
            currentIndex = (currentIndex + 1) % playList.size();
            playSong(currentIndex);
        });

        requestStoragePermission();
    }

    private void requestStoragePermission(){
        String permission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? Manifest.permission.READ_MEDIA_AUDIO
                : Manifest.permission.READ_EXTERNAL_STORAGE;

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            loadAudioFiles();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{permission}, PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == PERMISSION_REQUEST_CODE && grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED){
            loadAudioFiles();
        }else{
            Toast.makeText(this, "Permisson denied, cannot load songs",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void loadAudioFiles(){
        Uri collection = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                ? MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                : MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String[] projection = new String[] { MediaStore.Audio.Media._ID };
        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";
        try (Cursor cursor = getContentResolver().query(collection, projection, selection, null, null)) {
            if (cursor != null) {
                int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idColumn);
                    Uri contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
                    playList.add(contentUri);
                }
            }
        }

        if (playList.isEmpty()) {
            Toast.makeText(this, "No MP3 files found on your device", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "Loaded " + playList.size() + " songs!", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if(mediaPlayer != null && mediaPlayer.isPlaying()){
            mediaPlayer.pause();
        }

        handler.removeCallbacks(updateSeekbar);
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
        handler.removeCallbacks(updateSeekbar);
        if(mediaPlayer != null){
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    private void playSong(int index){

        if(mediaPlayer != null){
            mediaPlayer.release();
        }

        Uri songUri = (Uri) playList.get(index);
        mediaPlayer = MediaPlayer.create(this, songUri);
        if(mediaPlayer == null) return;
        mediaPlayer.start();

        seekBar.setMax(mediaPlayer.getDuration());

        // Getting the total duration
        int durationInMilliseconds = mediaPlayer.getDuration();
        int minutes = (durationInMilliseconds / 1000) / 60;
        int seconds = (durationInMilliseconds / 1000) %60;
        String totalTime = String.format("%02d:%02d", minutes, seconds);
        totalPlayTime.setText(totalTime);

        updateTrackInfo(songUri);

        handler.removeCallbacks(updateSeekbar);
        handler.postDelayed(updateSeekbar, 0);
    }

    private void updateTrackInfo(Uri mediaUri){

        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(this, mediaUri);

        byte[] art = retriever.getEmbeddedPicture();
        if (art != null) {
            Bitmap bitmap = BitmapFactory.decodeByteArray(art, 0, art.length);
            albumArt.setImageBitmap(bitmap);
        } else {
            albumArt.setImageDrawable(null);
        }

        String title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
        if (title != null) {
            audioTitle.setText(title);
        } else {
            // Fallback if no ID3 tag exists
            audioTitle.setText("Unknown Track");
        }

        try {
            retriever.release();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}