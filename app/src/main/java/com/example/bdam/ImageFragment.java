package com.example.bdam;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.bumptech.glide.Glide;

public class ImageFragment extends Fragment {

    private static final String ARG_URI = "image_uri";

    public static ImageFragment newInstance(Uri imageUri) {
        ImageFragment fragment = new ImageFragment();
        Bundle args = new Bundle();
        args.putString(ARG_URI, imageUri.toString());
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_image, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ImageView imageView = view.findViewById(R.id.imageView_fragment);
        if (getArguments() != null) {
            String uriString = getArguments().getString(ARG_URI);
            Uri imageUri = Uri.parse(uriString);
            Glide.with(this).load(imageUri).into(imageView);
        }
    }
}