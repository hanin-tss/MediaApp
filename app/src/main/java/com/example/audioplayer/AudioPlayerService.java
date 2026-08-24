package com.example.audioplayer;

import android.content.ContentUris;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.media.MediaBrowserServiceCompat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AudioPlayerService extends MediaBrowserServiceCompat {
    private MediaPlayer mediaPlayer;
    private ArrayList<Uri> playList = new ArrayList<>();
    private int currentIndex = 0;

    // Remote reciever
    private MediaSessionCompat mediaSession;

    @Override
    public void onCreate(){
        super.onCreate();

        mediaSession = new MediaSessionCompat(this, "AudioPlayerService");

        setSessionToken(mediaSession.getSessionToken());
        mediaSession.setActive(true);

        // Connects the car's physical buttons to the media player
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay(){
                if(playList.isEmpty()){
                    loadAudioFiles();
                }

                if(mediaPlayer == null){
                    playSong(currentIndex);
                } else if(!mediaPlayer.isPlaying()) {
                    mediaPlayer.start();
                }

                setMediaPlaybackState(PlaybackStateCompat.STATE_PLAYING);

            }

            @Override
            public void onPause(){
                if(mediaPlayer != null && mediaPlayer.isPlaying()){
                    mediaPlayer.pause();
                    setMediaPlaybackState(PlaybackStateCompat.STATE_PAUSED);
                }
            }

            private void setMediaPlaybackState(int state) {
                PlaybackStateCompat playbackState = new PlaybackStateCompat.Builder()
                        .setActions(PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_PAUSE |
                                PlaybackStateCompat.ACTION_SKIP_TO_NEXT | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
                        .setState(state, mediaPlayer.getCurrentPosition(), 1.0f)
                        .build();
                mediaSession.setPlaybackState(playbackState);
            }

            @Override
            public void onSkipToNext(){
                currentIndex = (currentIndex + 1) % playList.size();
                playSong(currentIndex);
            }

            @Override
            public void onSkipToPrevious(){
                currentIndex = (currentIndex - 1 + playList.size()) % playList.size();
                playSong(currentIndex);
            }

            @Override
            public void onPlayFromMediaId(String mediaId, Bundle extras){
                try {
                    int requestedId = Integer.parseInt(mediaId);

                    if(requestedId >= 0 && requestedId < playList.size()){
                        currentIndex = requestedId;
                        playSong(currentIndex);
                    }
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                }
            }
        });
    }


    @Override
    public BrowserRoot onGetRoot(String clientPackageName, int clientUid, Bundle rootHints){
        return new BrowserRoot("root_id", null);
    }

    @Override
    public void onLoadChildren(String parentId, Result<List<MediaBrowserCompat.MediaItem>> result){
        if(playList.isEmpty()){
            loadAudioFiles();
        }

        List mediaItems = new ArrayList<>();

        for(int i=0; i<playList.size(); i++){
            Uri uri = (Uri) playList.get(i);

            // This description is exactly what the car will print on the screen
            MediaDescriptionCompat description = new MediaDescriptionCompat.Builder()
                    .setMediaId(String.valueOf(i)) // We use the array index as the ID
                    .setTitle("Track " + (i + 1))  // The text shown on the car screen
                    .setMediaUri(uri)
                    .build();

            // FLAG_PLAYABLE tells the car this is a song to play, not a folder to open
            MediaBrowserCompat.MediaItem item = new MediaBrowserCompat.MediaItem(description, MediaBrowserCompat.MediaItem.FLAG_PLAYABLE);
            mediaItems.add(item);
        }
        result.sendResult(mediaItems);
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
        mediaSession.setActive(false);
        mediaSession.release();
        if(mediaPlayer != null){
            mediaPlayer.release();
        }
    }

    private void loadAudioFiles() {
        playList.clear();
        Uri collection = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                ? MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                : MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String[] projection = new String[]{MediaStore.Audio.Media._ID};
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
    }

    private void playSong(int index) {
        if(playList.isEmpty()){
            return;
        }

        if (mediaPlayer != null) {
            mediaPlayer.release();
        }

        Uri songUri = (Uri) playList.get(index);
        mediaPlayer = MediaPlayer.create(this, songUri);
        if (mediaPlayer == null) return;
        mediaPlayer.start();

        // Auto-play the next song when this one finishes
        mediaPlayer.setOnCompletionListener(mp -> {
            if (playList.isEmpty()) return;

            currentIndex = (currentIndex + 1) % playList.size();

            playSong(currentIndex);
        });

        // Broadcasting the playback state
        PlaybackStateCompat state = new PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_PAUSE | PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
                .setState(PlaybackStateCompat.STATE_PLAYING, mediaPlayer.getCurrentPosition(), 1.0f)
                .build();

        mediaSession.setPlaybackState(state);

        // Extract and Broadcast the Metadata
        MediaMetadataRetriever metadataRetriever = new MediaMetadataRetriever();

        try {
            metadataRetriever.setDataSource(this, songUri);
            String title = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            byte art[] = metadataRetriever.getEmbeddedPicture();
            Bitmap bitmap = null;
            if (art != null) {
                bitmap = BitmapFactory.decodeByteArray(art, 0, art.length);
            }

            MediaMetadataCompat metadata = new MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title != null ? title : "Unknown Track")
                    .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, mediaPlayer.getDuration())
                    .build();
            mediaSession.setMetadata(metadata);
        } catch(Exception e){
            e.printStackTrace();
        } finally {
            try {
                metadataRetriever.release();
            } catch (Exception e) {}
        }


    }
}
