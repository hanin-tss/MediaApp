package com.example.audioplayer;

import android.support.v4.media.MediaBrowserCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class SongAdapter extends RecyclerView.Adapter<SongAdapter.SongViewHolder> {

    private List<MediaBrowserCompat.MediaItem> songList = new ArrayList<>();
    private final OnSongClickListener listener;

    // Interface so the Activity can handle the clicks
    public interface OnSongClickListener {
        void onSongClick(MediaBrowserCompat.MediaItem item);
    }

    public SongAdapter(OnSongClickListener listener) {
        this.listener = listener;
    }

    public void submitList(List<MediaBrowserCompat.MediaItem> newSongs) {
        songList.clear();
        songList.addAll(newSongs);
        notifyDataSetChanged(); // Refreshes the list UI
    }

    @NonNull
    @Override
    public SongViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_song, parent, false);
        return new SongViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SongViewHolder holder, int position) {
        MediaBrowserCompat.MediaItem item = songList.get(position);
        // We use the title we generated in the Service's onLoadChildren method
        holder.tvTitle.setText(item.getDescription().getTitle());

        holder.itemView.setOnClickListener(v -> listener.onSongClick(item));
    }

    @Override
    public int getItemCount() {
        return songList.size();
    }

    static class SongViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle;
        public SongViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvItemTitle);
        }
    }
}