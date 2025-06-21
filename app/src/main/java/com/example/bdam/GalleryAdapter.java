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

public class GalleryAdapter extends RecyclerView.Adapter<GalleryAdapter.ImageViewHolder> {

    private final Context context;
    private final List<Uri> imageUris;
    private final List<Uri> selectedUris = new ArrayList<>();
    private final GalleryItemListener listener;

    public interface GalleryItemListener {
        void onSelectionModeChanged(boolean isSelectionMode);
        void onItemClicked(Uri uri);
    }

    public GalleryAdapter(Context context, List<Uri> imageUris, GalleryItemListener listener) {
        this.context = context;
        this.imageUris = imageUris;
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
        Uri currentUri = imageUris.get(position);
        Glide.with(context).load(currentUri).centerCrop().into(holder.imageView);

        boolean isSelected = selectedUris.contains(currentUri);
        holder.checkView.setVisibility(isSelected ? View.VISIBLE : View.GONE);
        holder.overlayView.setVisibility(isSelected ? View.VISIBLE : View.GONE);

        holder.itemView.setOnClickListener(v -> {
            if (!selectedUris.isEmpty()) {
                toggleSelection(currentUri);
            } else {
                listener.onItemClicked(currentUri);
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            toggleSelection(currentUri);
            return true;
        });
    }

    private void toggleSelection(Uri uri) {
        boolean wasEmpty = selectedUris.isEmpty();
        if (selectedUris.contains(uri)) {
            selectedUris.remove(uri);
        } else {
            selectedUris.add(uri);
        }
        boolean isEmptyNow = selectedUris.isEmpty();
        if (wasEmpty != isEmptyNow) {
            listener.onSelectionModeChanged(!isEmptyNow);
        }
        notifyDataSetChanged();
    }

    public List<Uri> getSelectedUris() {
        return new ArrayList<>(selectedUris);
    }

    public void clearSelections() {
        boolean wasInSelectionMode = !selectedUris.isEmpty();
        selectedUris.clear();
        if (wasInSelectionMode) {
            listener.onSelectionModeChanged(false);
        }
        notifyDataSetChanged();
    }

    public int getSelectedItemCount() {
        return selectedUris.size();
    }

    @Override
    public int getItemCount() {
        return imageUris.size();
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