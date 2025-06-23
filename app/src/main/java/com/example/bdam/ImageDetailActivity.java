package com.example.bdam;

import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ImageDetailActivity extends AppCompatActivity {

    // Keys để nhận dữ liệu từ Intent
    public static final String EXTRA_IMAGE_URIS = "IMAGE_URIS";
    public static final String EXTRA_INITIAL_POSITION = "INITIAL_POSITION";

    private ViewPager2 viewPager;
    private ImageViewerAdapter adapter;
    private List<Uri> imageUris;
    private int currentPosition;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_detail);
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        viewPager = findViewById(R.id.viewPager_images);
        ImageButton buttonBack = findViewById(R.id.button_detail_back);
        ImageButton buttonShare = findViewById(R.id.button_share_detail);
        ImageButton buttonDelete = findViewById(R.id.button_delete_detail);

        // Nhận dữ liệu từ GalleryActivity
        ArrayList<String> uriStrings = getIntent().getStringArrayListExtra(EXTRA_IMAGE_URIS);
        int initialPosition = getIntent().getIntExtra(EXTRA_INITIAL_POSITION, 0);
        currentPosition = initialPosition;

        // Chuyển đổi lại từ String sang Uri
        imageUris = uriStrings.stream().map(Uri::parse).collect(Collectors.toList());

        // Thiết lập adapter cho ViewPager2
        adapter = new ImageViewerAdapter(this, imageUris);
        viewPager.setAdapter(adapter);

        // Hiển thị ảnh được chọn ban đầu
        viewPager.setCurrentItem(initialPosition, false);

        // Cập nhật currentPosition khi người dùng vuốt
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                currentPosition = position;
            }
        });

        // Xử lý sự kiện nút
        buttonBack.setOnClickListener(v -> finish());
        buttonDelete.setOnClickListener(v -> showDeleteConfirmationDialog());
        buttonShare.setOnClickListener(v -> shareCurrentImage());
    }

    private void shareCurrentImage() {
        if (imageUris.isEmpty()) return;
        Uri uriToShare = imageUris.get(currentPosition);
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("image/jpeg");
        shareIntent.putExtra(Intent.EXTRA_STREAM, uriToShare);
        startActivity(Intent.createChooser(shareIntent, "Chia sẻ ảnh..."));
    }

    private void showDeleteConfirmationDialog() {
        if (imageUris.isEmpty()) return;
        new AlertDialog.Builder(this)
                .setTitle("Xác nhận xóa")
                .setMessage("Bạn có chắc chắn muốn xóa ảnh này không?")
                .setPositiveButton("Xóa", (dialog, which) -> deleteImageAtPosition(currentPosition))
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void deleteImageAtPosition(int position) {
        ContentResolver resolver = getContentResolver();
        Uri uriToDelete = imageUris.get(position);
        try {
            // Thực hiện xóa ảnh
            int rowsDeleted = resolver.delete(uriToDelete, null, null);
            if (rowsDeleted > 0) {
                Toast.makeText(this, "Đã xóa ảnh", Toast.LENGTH_SHORT).show();
                // Xóa khỏi danh sách và cập nhật adapter
                imageUris.remove(position);
                adapter.notifyItemRemoved(position);

                // Nếu không còn ảnh nào, đóng activity
                if (imageUris.isEmpty()) {
                    finish();
                }
            } else {
                Toast.makeText(this, "Không thể xóa ảnh", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Lỗi khi xóa ảnh: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}