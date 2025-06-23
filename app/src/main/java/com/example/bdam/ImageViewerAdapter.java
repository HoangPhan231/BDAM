package com.example.bdam;

import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import java.util.List;

public class ImageViewerAdapter extends FragmentStateAdapter {

    private final List<Uri> imageUris;

    public ImageViewerAdapter(@NonNull FragmentActivity fragmentActivity, List<Uri> imageUris) {
        super(fragmentActivity);
        this.imageUris = imageUris;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        // Trả về một ImageFragment mới cho vị trí được yêu cầu
        return ImageFragment.newInstance(imageUris.get(position));
    }

    @Override
    public int getItemCount() {
        return imageUris.size();
    }
}