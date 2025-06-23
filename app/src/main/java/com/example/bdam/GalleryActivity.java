package com.example.bdam;

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.RecoverableSecurityException;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.content.IntentSender;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
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
import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class GalleryActivity extends AppCompatActivity implements GalleryAdapter.GalleryItemListener {

    public static class MediaItem {
        final Uri uri;
        final String displayName;

        MediaItem(Uri uri, String displayName) {
            this.uri = uri;
            this.displayName = displayName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MediaItem mediaItem = (MediaItem) o;
            return uri.equals(mediaItem.uri);
        }

        @Override
        public int hashCode() {
            return Objects.hash(uri);
        }
    }

    private ImageButton buttonGalleryBack, buttonSearch, buttonAdd, buttonShare, buttonDelete;
    private TextView galleryTitle;
    private RecyclerView recyclerViewGallery;
    private SearchView searchView;
    private GalleryAdapter galleryAdapter;

    private final List<MediaItem> allMediaItems = new ArrayList<>();
    private final List<MediaItem> filteredMediaItems = new ArrayList<>();
    private boolean isSelectionMode = false;


    private final ActivityResultLauncher<IntentSenderRequest> deleteResultLauncher =
            registerForActivityResult(new ActivityResultContracts.StartIntentSenderForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    Toast.makeText(this, "Đã xóa thành công.", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Hủy xóa.", Toast.LENGTH_SHORT).show();
                }
                if (galleryAdapter != null) galleryAdapter.clearSelections();
                loadMediaFromStorage();
            });

    // --- LOGIC MỚI BẮT ĐẦU TỪ ĐÂY ---
    private final ActivityResultLauncher<Intent> addImageLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri selectedImageUri = result.getData().getData();
                    if (selectedImageUri != null) {
                        // Thực hiện việc sao chép trên một luồng nền để không làm treo UI
                        ExecutorService executor = Executors.newSingleThreadExecutor();
                        executor.execute(() -> {
                            boolean success = copyImageToAppGallery(selectedImageUri);
                            runOnUiThread(() -> {
                                if (success) {
                                    Toast.makeText(this, "Đã thêm ảnh vào thư viện.", Toast.LENGTH_SHORT).show();
                                    loadMediaFromStorage(); // Tải lại thư viện để hiển thị ảnh mới
                                } else {
                                    Toast.makeText(this, "Thêm ảnh thất bại.", Toast.LENGTH_SHORT).show();
                                }
                            });
                        });
                    }
                }
            });

    // Hàm mới để sao chép ảnh vào thư mục của ứng dụng
    private boolean copyImageToAppGallery(Uri sourceUri) {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "IMPORTED_" + timeStamp + ".jpg";

        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, imageFileName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
        // Lưu vào thư mục Pictures/Tên_Ứng_Dụng
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + File.separator + getString(R.string.app_name));
        }

        ContentResolver resolver = getContentResolver();
        Uri destinationUri = null;

        try {
            destinationUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (destinationUri == null) {
                throw new IOException("Failed to create new MediaStore record.");
            }

            try (InputStream inputStream = resolver.openInputStream(sourceUri);
                 OutputStream outputStream = resolver.openOutputStream(destinationUri)) {

                if (inputStream == null || outputStream == null) {
                    throw new IOException("Failed to open input or output stream.");
                }

                // Sao chép dữ liệu từ stream này sang stream khác
                byte[] buf = new byte[8192];
                int len;
                while ((len = inputStream.read(buf)) > 0) {
                    outputStream.write(buf, 0, len);
                }
            }
            return true;
        } catch (IOException e) {
            Log.e("GalleryActivity", "Failed to copy image", e);
            // Nếu có lỗi, xóa bản ghi đã tạo trong MediaStore
            if (destinationUri != null) {
                resolver.delete(destinationUri, null, null);
            }
            return false;
        }
    }
    // --- LOGIC MỚI KẾT THÚC TẠI ĐÂY ---


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gallery);
        if (getSupportActionBar() != null) { getSupportActionBar().hide(); }

        buttonGalleryBack = findViewById(R.id.button_gallery_back);
        galleryTitle = findViewById(R.id.gallery_title);
        searchView = findViewById(R.id.search_view_gallery);
        buttonSearch = findViewById(R.id.button_search_gallery);
        buttonAdd = findViewById(R.id.button_add_from_gallery);
        buttonShare = findViewById(R.id.button_share_gallery);
        buttonDelete = findViewById(R.id.button_delete_gallery);
        recyclerViewGallery = findViewById(R.id.recyclerView_gallery);

        setupRecyclerView();
        setupEventListeners();
        updateToolbarUI();
    }

    private void setupRecyclerView() {
        recyclerViewGallery.setLayoutManager(new GridLayoutManager(this, 4));
        galleryAdapter = new GalleryAdapter(this, filteredMediaItems, this);
        recyclerViewGallery.setAdapter(galleryAdapter);
    }

    private void loadMediaFromStorage() {
        allMediaItems.clear();
        String[] projection = {
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.MEDIA_TYPE,
                MediaStore.Files.FileColumns.DISPLAY_NAME
        };
        String selection = MediaStore.Files.FileColumns.MEDIA_TYPE + "="
                + MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE
                + " OR "
                + MediaStore.Files.FileColumns.MEDIA_TYPE + "="
                + MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO;
        Uri queryUri = MediaStore.Files.getContentUri("external");

        try (Cursor cursor = getContentResolver().query(queryUri, projection, selection, null, MediaStore.Files.FileColumns.DATE_ADDED + " DESC")) {
            if (cursor != null) {
                int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID);
                int nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME);
                int mediaTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE);

                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idColumn);
                    String name = cursor.getString(nameColumn);
                    int mediaType = cursor.getInt(mediaTypeColumn);

                    Uri contentUri;
                    if (mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE) {
                        contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
                    } else if (mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) {
                        contentUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id);
                    } else {
                        continue;
                    }

                    allMediaItems.add(new MediaItem(contentUri, name));
                }
            }
        } catch (Exception e) {
            Log.e("GalleryActivity", "Lỗi khi tải media từ bộ nhớ", e);
            Toast.makeText(this, "Không thể tải thư viện.", Toast.LENGTH_SHORT).show();
        }

        String currentQuery = searchView.getQuery() != null ? searchView.getQuery().toString() : "";
        filterMedia(currentQuery);
    }

    private void filterMedia(String query) {
        filteredMediaItems.clear();
        if (query.isEmpty()) {
            filteredMediaItems.addAll(allMediaItems);
        } else {
            for (MediaItem item : allMediaItems) {
                if (item.displayName.toLowerCase().contains(query.toLowerCase())) {
                    filteredMediaItems.add(item);
                }
            }
        }
        if(galleryAdapter != null){
            galleryAdapter.notifyDataSetChanged();
        }
    }

    private void setupEventListeners() {
        buttonGalleryBack.setOnClickListener(v -> {
            if (isSelectionMode) {
                galleryAdapter.clearSelections();
            } else {
                finish();
            }
        });

        buttonSearch.setOnClickListener(v -> {
            if (searchView.getVisibility() == View.VISIBLE) {
                searchView.setQuery("", false);
                searchView.setVisibility(View.GONE);
                galleryTitle.setVisibility(View.VISIBLE);
            } else {
                searchView.setVisibility(View.VISIBLE);
                galleryTitle.setVisibility(View.GONE);
                searchView.requestFocus();
            }
        });

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                filterMedia(query);
                return true;
            }
            @Override
            public boolean onQueryTextChange(String newText) {
                filterMedia(newText);
                return true;
            }
        });

        buttonAdd.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType("image/*");
            addImageLauncher.launch(intent);
        });

        buttonShare.setOnClickListener(v -> shareSelectedItems());
        buttonDelete.setOnClickListener(v -> deleteSelectedItems());
    }

    private void updateToolbarUI() {
        if (isSelectionMode) {
            int count = galleryAdapter.getSelectedItemCount();
            galleryTitle.setText("Đã chọn " + count);
            galleryTitle.setVisibility(View.VISIBLE);
            searchView.setVisibility(View.GONE);
            buttonSearch.setVisibility(View.GONE);
            buttonAdd.setVisibility(View.GONE);
            buttonShare.setVisibility(View.VISIBLE);
            buttonDelete.setVisibility(View.VISIBLE);
        } else {
            galleryTitle.setText("Thư viện");
            galleryTitle.setVisibility(View.VISIBLE);
            searchView.setVisibility(View.GONE);
            buttonSearch.setVisibility(View.VISIBLE);
            buttonAdd.setVisibility(View.VISIBLE);
            buttonShare.setVisibility(View.GONE);
            buttonDelete.setVisibility(View.GONE);
        }
    }

    private void shareSelectedItems() {
        List<Uri> selectedUris = galleryAdapter.getSelectedUris();
        if (selectedUris.isEmpty()) return;
        Intent shareIntent = new Intent(Intent.ACTION_SEND_MULTIPLE);
        shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, new ArrayList<>(selectedUris));
        shareIntent.setType("*/*");
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(shareIntent, "Chia sẻ..."));
    }

    private void deleteSelectedItems() {
        List<Uri> selectedUris = galleryAdapter.getSelectedUris();
        if (selectedUris.isEmpty()) return;
        new AlertDialog.Builder(this)
                .setTitle("Xác nhận xóa")
                .setMessage("Bạn có chắc chắn muốn xóa " + selectedUris.size() + " mục đã chọn?")
                .setPositiveButton("Xóa", (dialog, which) -> performDelete(selectedUris))
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void performDelete(List<Uri> urisToDelete) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                PendingIntent pendingIntent = MediaStore.createDeleteRequest(getContentResolver(), urisToDelete);
                IntentSenderRequest request = new IntentSenderRequest.Builder(pendingIntent.getIntentSender()).build();
                deleteResultLauncher.launch(request);
            } else {
                for (Uri uri : urisToDelete) {
                    getContentResolver().delete(uri, null, null);
                }
                Toast.makeText(this, "Đã xóa thành công.", Toast.LENGTH_SHORT).show();
                galleryAdapter.clearSelections();
                loadMediaFromStorage();
            }
        } catch (SecurityException e) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e instanceof RecoverableSecurityException) {
                RecoverableSecurityException rse = (RecoverableSecurityException) e;
                IntentSenderRequest request = new IntentSenderRequest.Builder(rse.getUserAction().getActionIntent().getIntentSender()).build();
                deleteResultLauncher.launch(request);
            } else {
                Toast.makeText(this, "Không có quyền xóa tệp.", Toast.LENGTH_SHORT).show();
                Log.e("GalleryActivity", "SecurityException on delete", e);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Lỗi khi xóa tệp.", Toast.LENGTH_SHORT).show();
            Log.e("GalleryActivity", "Generic exception on delete", e);
        }
    }


    @Override
    public void onSelectionModeChanged(boolean isSelectionMode) {
        this.isSelectionMode = isSelectionMode;
        updateToolbarUI();
    }

    @Override
    public void onItemClicked(int position) {
        Intent intent = new Intent(this, ImageDetailActivity.class);

        ArrayList<String> uriStrings = filteredMediaItems.stream()
                .map(item -> item.uri.toString())
                .collect(Collectors.toCollection(ArrayList::new));

        intent.putStringArrayListExtra(ImageDetailActivity.EXTRA_IMAGE_URIS, uriStrings);
        intent.putExtra(ImageDetailActivity.EXTRA_INITIAL_POSITION, position);
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadMediaFromStorage();
    }

    @Override
    public void onBackPressed() {
        if (isSelectionMode) {
            galleryAdapter.clearSelections();
        } else if (searchView.getVisibility() == View.VISIBLE && !searchView.isIconified()) {
            searchView.setQuery("", false);
            searchView.setVisibility(View.GONE);
            galleryTitle.setVisibility(View.VISIBLE);
        }
        else {
            super.onBackPressed();
        }
    }
}
