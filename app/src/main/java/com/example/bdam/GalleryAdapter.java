package com.example.bdam;

import android.content.Context;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class GalleryAdapter extends RecyclerView.Adapter<GalleryAdapter.ImageViewHolder> {

    private final Context context;
    // Làm việc với danh sách MediaItem từ GalleryActivity
    private final List<GalleryActivity.MediaItem> mediaItems;
    private final List<GalleryActivity.MediaItem> selectedItems = new ArrayList<>();
    private final GalleryItemListener listener;

    public interface GalleryItemListener {
        void onSelectionModeChanged(boolean isSelectionMode);
        void onItemClicked(int position);
    }

    // Constructor chấp nhận List<GalleryActivity.MediaItem>
    public GalleryAdapter(Context context, List<GalleryActivity.MediaItem> mediaItems, GalleryItemListener listener) {
        this.context = context;
        this.mediaItems = mediaItems;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.gallery_item_layout, parent, false);
        return new ImageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ImageViewHolder holder, int position) {
        GalleryActivity.MediaItem currentItem = mediaItems.get(position);

        Glide.with(context).load(currentItem.uri).centerCrop().into(holder.imageView);

        boolean isSelected = selectedItems.contains(currentItem);
        holder.checkView.setVisibility(isSelected ? View.VISIBLE : View.GONE);
        holder.overlayView.setVisibility(isSelected ? View.VISIBLE : View.GONE);

        holder.itemView.setOnClickListener(v -> {
            if (!selectedItems.isEmpty()) {
                toggleSelection(currentItem);
            } else {
                listener.onItemClicked(holder.getAdapterPosition());
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            toggleSelection(currentItem);
            return true;
        });
    }

    private void toggleSelection(GalleryActivity.MediaItem item) {
        boolean wasEmpty = selectedItems.isEmpty();
        if (selectedItems.contains(item)) {
            selectedItems.remove(item);
        } else {
            selectedItems.add(item);
        }
        boolean isEmptyNow = selectedItems.isEmpty();
        if (wasEmpty != isEmptyNow) {
            listener.onSelectionModeChanged(!isEmptyNow);
        }
        notifyDataSetChanged();
    }

    public List<Uri> getSelectedUris() {
        return selectedItems.stream().map(item -> item.uri).collect(Collectors.toList());
    }

    public void clearSelections() {
        boolean wasInSelectionMode = !selectedItems.isEmpty();
        selectedItems.clear();
        if (wasInSelectionMode) {
            listener.onSelectionModeChanged(false);
        }
        notifyDataSetChanged();
    }

    public int getSelectedItemCount() {
        return selectedItems.size();
    }

    @Override
    public int getItemCount() {
        return mediaItems.size();
    }

    static class ImageViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView, checkView;
        View overlayView;

        public ImageViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.imageView_gallery_item);
            checkView = itemView.findViewById(R.id.imageView_check);
            overlayView = itemView.findViewById(R.id.view_overlay);
        }
    }
}
