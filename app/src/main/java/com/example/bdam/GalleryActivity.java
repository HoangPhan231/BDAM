package com.example.bdam;

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class GalleryActivity extends AppCompatActivity implements GalleryAdapter.GalleryItemListener {

    private ImageButton buttonGalleryBack, buttonSelectMultiple, buttonShareGallery, buttonDeleteGallery;
    private TextView galleryTitle;
    private RecyclerView recyclerViewGallery;
    private GalleryAdapter galleryAdapter;
    private final List<Uri> imageUris = new ArrayList<>();
    private boolean isSelectionMode = false;

    private final ActivityResultLauncher<IntentSenderRequest> deleteResultLauncher =
            registerForActivityResult(new ActivityResultContracts.StartIntentSenderForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    Toast.makeText(this, "Đã xóa ảnh thành công.", Toast.LENGTH_SHORT).show();
                    galleryAdapter.clearSelections();
                    loadImagesFromMediaStore();
                } else {
                    Toast.makeText(this, "Hủy xóa ảnh.", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gallery);
        if (getSupportActionBar() != null) { getSupportActionBar().hide(); }

        buttonGalleryBack = findViewById(R.id.button_gallery_back);
        galleryTitle = findViewById(R.id.gallery_title);
        buttonSelectMultiple = findViewById(R.id.button_select_multiple);
        buttonShareGallery = findViewById(R.id.button_share_gallery);
        buttonDeleteGallery = findViewById(R.id.button_delete_gallery);
        recyclerViewGallery = findViewById(R.id.recyclerView_gallery);

        setupRecyclerView();
        loadImagesFromMediaStore();
        setupEventListeners();
        updateToolbar();
    }

    private void setupRecyclerView() {
        recyclerViewGallery.setLayoutManager(new GridLayoutManager(this, 4));
        galleryAdapter = new GalleryAdapter(this, imageUris, this);
        recyclerViewGallery.setAdapter(galleryAdapter);
    }

    private void loadImagesFromMediaStore() {
        imageUris.clear();
        String[] projection = {MediaStore.Images.Media._ID};
        String sortOrder = MediaStore.Images.Media.DATE_ADDED + " DESC";
        try (Cursor cursor = getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, null, null, sortOrder)) {
            if (cursor != null) {
                int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idColumn);
                    imageUris.add(ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id));
                }
            }
        }
        galleryAdapter.notifyDataSetChanged();
    }

    private void setupEventListeners() {
        buttonGalleryBack.setOnClickListener(v -> {
            if (isSelectionMode) {
                galleryAdapter.clearSelections();
            } else {
                finish();
            }
        });
        buttonSelectMultiple.setOnClickListener(v -> Toast.makeText(this, "Nhấn giữ một ảnh để bắt đầu chọn.", Toast.LENGTH_SHORT).show());
        buttonShareGallery.setOnClickListener(v -> shareSelectedImages());
        buttonDeleteGallery.setOnClickListener(v -> deleteSelectedImages());
    }

    private void updateToolbar() {
        if (isSelectionMode) {
            int count = galleryAdapter.getSelectedItemCount();
            galleryTitle.setText("Đã chọn " + count);
            buttonGalleryBack.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
            buttonSelectMultiple.setVisibility(View.GONE);
            buttonShareGallery.setVisibility(View.VISIBLE);
            buttonDeleteGallery.setVisibility(View.VISIBLE);
        } else {
            galleryTitle.setText("Thư viện"); // Hoặc R.string.gallery_title
            buttonGalleryBack.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
            buttonSelectMultiple.setVisibility(View.VISIBLE);
            buttonShareGallery.setVisibility(View.GONE);
            buttonDeleteGallery.setVisibility(View.GONE);
        }
    }

    private void shareSelectedImages() {
        List<Uri> selectedUris = galleryAdapter.getSelectedUris();
        if (selectedUris.isEmpty()) return;
        Intent shareIntent = new Intent(Intent.ACTION_SEND_MULTIPLE);
        shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, new ArrayList<>(selectedUris));
        shareIntent.setType("image/*");
        startActivity(Intent.createChooser(shareIntent, "Chia sẻ ảnh..."));
    }

    private void deleteSelectedImages() {
        List<Uri> selectedUris = galleryAdapter.getSelectedUris();
        if (selectedUris.isEmpty()) return;
        new AlertDialog.Builder(this)
                .setTitle("Xác nhận xóa")
                .setMessage("Bạn có chắc chắn muốn xóa " + selectedUris.size() + " ảnh đã chọn?")
                .setPositiveButton("Xóa", (dialog, which) -> performDelete(selectedUris))
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void performDelete(List<Uri> urisToDelete) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                PendingIntent pendingIntent = MediaStore.createDeleteRequest(getContentResolver(), urisToDelete);
                deleteResultLauncher.launch(new IntentSenderRequest.Builder(pendingIntent.getIntentSender()).build());
            } else {
                ContentResolver contentResolver = getContentResolver();
                for (Uri uri : urisToDelete) {
                    contentResolver.delete(uri, null, null);
                }
                Toast.makeText(this, "Đã xóa ảnh thành công.", Toast.LENGTH_SHORT).show();
                galleryAdapter.clearSelections();
                loadImagesFromMediaStore();
            }
        } catch (Exception e) {
            Log.e("GalleryActivity", "Error deleting images", e);
            Toast.makeText(this, "Lỗi khi xóa ảnh.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onSelectionModeChanged(boolean isSelectionMode) {
        this.isSelectionMode = isSelectionMode;
        updateToolbar();
    }

    @Override
    public void onItemClicked(Uri uri) {
        Toast.makeText(this, "Mở ảnh: " + uri.toString(), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onBackPressed() {
        if (isSelectionMode) {
            galleryAdapter.clearSelections();
        } else {
            super.onBackPressed();
        }
    }
}