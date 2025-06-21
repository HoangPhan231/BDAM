package com.example.bdam;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "BDAM_CameraApp";
    private static final int REQUEST_PERMISSIONS = 1;

    // --- Views ---
    private TextureView textureView;
    private EditText editTextTextToAdd;
    private ImageButton buttonCapture;
    private ImageButton buttonSettings;
    private ImageButton buttonFlashAuto, buttonFlashOn, buttonFlashOff;
    private ImageButton buttonCameraSwitch;
    private ImageButton buttonGalleryThumbnail;
    private TextView textPhoto, textVideo;
    private TextView textOverlayDisplay;
    private View focusOverlay;

    // --- Camera2 API Variables ---
    private CameraDevice cameraDevice;
    private CameraCaptureSession cameraCaptureSession;
    private CaptureRequest.Builder previewCaptureRequestBuilder;
    private Size previewSize;
    private String cameraId;
    private int cameraFacing = CameraCharacteristics.LENS_FACING_BACK;
    private ImageReader imageReader;

    // --- Background Thread for Camera Operations ---
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;

    // --- Semaphore to prevent camera from closing prematurely ---
    private Semaphore cameraOpenCloseLock = new Semaphore(1);

    // --- Text Properties ---
    private String currentText = "";
    private float textSize = 40f;
    private TextPosition currentTextPosition = TextPosition.BOTTOM_RIGHT;

    private enum TextPosition {
        TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
    }

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraOpenCloseLock.release();
            cameraDevice = camera;
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            cameraOpenCloseLock.release();
            camera.close();
            cameraDevice = null;
            Toast.makeText(MainActivity.this, "Camera đã ngắt kết nối.", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            cameraOpenCloseLock.release();
            camera.close();
            cameraDevice = null;
            Toast.makeText(MainActivity.this, "Lỗi camera: " + error, Toast.LENGTH_SHORT).show();
            finish();
        }
    };

    private final TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
            openCamera(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {
        }

        @Override
        public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {
            updateTimestampOverlay();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textureView = findViewById(R.id.textureView);
        editTextTextToAdd = findViewById(R.id.editText_textToAdd);
        buttonCapture = findViewById(R.id.button_capture);
        buttonSettings = findViewById(R.id.button_settings);
        buttonFlashAuto = findViewById(R.id.button_flash_auto);
        buttonFlashOn = findViewById(R.id.button_flash_on);
        buttonFlashOff = findViewById(R.id.button_flash_off);
        buttonCameraSwitch = findViewById(R.id.button_camera_switch);
        buttonGalleryThumbnail = findViewById(R.id.button_gallery_thumbnail);
        textPhoto = findViewById(R.id.text_photo);
        textVideo = findViewById(R.id.text_video);
        textOverlayDisplay = findViewById(R.id.text_overlay_display);
        focusOverlay = findViewById(R.id.focus_overlay);

        if (checkAndRequestPermissions()) {
            setupCameraTextureView();
        }

        setupEventListeners();

        textureView.setOnTouchListener((v, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                float x = event.getX();
                float y = event.getY();
                focusOverlay.setX(x - focusOverlay.getWidth() / 2);
                focusOverlay.setY(y - focusOverlay.getHeight() / 2);
                focusOverlay.setVisibility(View.VISIBLE);
                focusOverlay.postDelayed(() -> focusOverlay.setVisibility(View.GONE), 1000);
            }
            return true;
        });

        updateTimestampOverlay();
        updateGalleryThumbnail();
    }

    @Override
    protected void onResume() {
        super.onResume();
        startBackgroundThread();
        if (textureView.isAvailable()) {
            openCamera(textureView.getWidth(), textureView.getHeight());
        } else {
            textureView.setSurfaceTextureListener(textureListener);
        }
        updateGalleryThumbnail();
    }

    @Override
    protected void onPause() {
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    private boolean checkAndRequestPermissions() {
        List<String> permissionsNeeded = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.CAMERA);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.RECORD_AUDIO);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
        }
        if (!permissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsNeeded.toArray(new String[0]), REQUEST_PERMISSIONS);
            return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            boolean allGranted = true;
            for (int grantResult : grantResults) {
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                setupCameraTextureView();
                if (textureView.isAvailable()) {
                    openCamera(textureView.getWidth(), textureView.getHeight());
                }
            } else {
                Toast.makeText(this, "BDAM cần các quyền để hoạt động.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void setupEventListeners() {
        buttonCapture.setOnClickListener(v -> takePicture());
        buttonSettings.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
        });

        buttonFlashAuto.setOnClickListener(v -> {
            // Logic for Flash Auto
        });
        buttonFlashOn.setOnClickListener(v -> {
            // Logic for Flash On
        });
        buttonFlashOff.setOnClickListener(v -> {
            // Logic for Flash Off
        });

        buttonCameraSwitch.setOnClickListener(v -> switchCamera());
        buttonGalleryThumbnail.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, GalleryActivity.class);
            startActivity(intent);
        });

        textPhoto.setOnClickListener(v -> {
            textPhoto.setTextColor(Color.WHITE);
            textPhoto.setTypeface(null, android.graphics.Typeface.BOLD);
            textVideo.setTextColor(Color.parseColor("#CCCCCC"));
            textVideo.setTypeface(null, android.graphics.Typeface.NORMAL);
        });
        textVideo.setOnClickListener(v -> {
            textVideo.setTextColor(Color.WHITE);
            textVideo.setTypeface(null, android.graphics.Typeface.BOLD);
            textPhoto.setTextColor(Color.parseColor("#CCCCCC"));
            textPhoto.setTypeface(null, android.graphics.Typeface.NORMAL);
        });
    }

    private void updateTimestampOverlay() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        String currentDateTime = sdf.format(new Date());
        textOverlayDisplay.setText(currentDateTime);
    }

    private void updateGalleryThumbnail() {
        String[] projection = { MediaStore.Images.Media._ID, MediaStore.Images.Media.DATA };
        String sortOrder = MediaStore.Images.Media.DATE_ADDED + " DESC";
        Uri uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        try (android.database.Cursor cursor = getContentResolver().query(uri, projection, null, null, sortOrder + " LIMIT 1")) {
            if (cursor != null && cursor.moveToFirst()) {
                int dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                String imagePath = cursor.getString(dataColumn);
                Bitmap thumbnailBitmap = android.graphics.BitmapFactory.decodeFile(imagePath);
                if (thumbnailBitmap != null) {
                    buttonGalleryThumbnail.setImageBitmap(thumbnailBitmap);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating gallery thumbnail: " + e.getMessage(), e);
        }
    }

    private void setupCameraTextureView() {
        textureView.setSurfaceTextureListener(textureListener);
    }

    @SuppressWarnings("MissingPermission")
    private void openCamera(int width, int height) {
        if (!checkAndRequestPermissions()) return;
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String camId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(camId);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == cameraFacing) {
                    cameraId = camId;
                    StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    if (map == null) continue;
                    previewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class), width, height);
                    Size largestImageSize = Collections.max(Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)), new CompareSizesByArea());
                    imageReader = ImageReader.newInstance(largestImageSize.getWidth(), largestImageSize.getHeight(), ImageFormat.JPEG, 2);
                    imageReader.setOnImageAvailableListener(onImageAvailableListener, backgroundHandler);
                    if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                        throw new RuntimeException("Time out waiting to lock camera opening.");
                    }
                    manager.openCamera(cameraId, stateCallback, backgroundHandler);
                    return;
                }
            }
        } catch (CameraAccessException | InterruptedException e) {
            Log.e(TAG, "Cannot access camera", e);
        }
    }

    private void switchCamera() {
        closeCamera();
        cameraFacing = (cameraFacing == CameraCharacteristics.LENS_FACING_BACK) ? CameraCharacteristics.LENS_FACING_FRONT : CameraCharacteristics.LENS_FACING_BACK;
        if (textureView.isAvailable()) {
            openCamera(textureView.getWidth(), textureView.getHeight());
        } else {
            textureView.setSurfaceTextureListener(textureListener);
        }
    }

    private void closeCamera() {
        try {
            cameraOpenCloseLock.acquire();
            if (cameraCaptureSession != null) {
                cameraCaptureSession.close();
                cameraCaptureSession = null;
            }
            if (cameraDevice != null) {
                cameraDevice.close();
                cameraDevice = null;
            }
            if (imageReader != null) {
                imageReader.close();
                imageReader = null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            cameraOpenCloseLock.release();
        }
    }

    private void createCameraPreviewSession() {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            Surface previewSurface = new Surface(texture);
            previewCaptureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewCaptureRequestBuilder.addTarget(previewSurface);
            cameraDevice.createCaptureSession(Arrays.asList(previewSurface, imageReader.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    if (cameraDevice == null) return;
                    cameraCaptureSession = session;
                    try {
                        previewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                        cameraCaptureSession.setRepeatingRequest(previewCaptureRequestBuilder.build(), null, backgroundHandler);
                    } catch (CameraAccessException e) {
                        Log.e(TAG, "Failed to start camera preview", e);
                    }
                }
                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    Toast.makeText(MainActivity.this, "Cấu hình camera thất bại.", Toast.LENGTH_SHORT).show();
                }
            }, backgroundHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Failed to create camera preview session", e);
        }
    }

    private static Size chooseOptimalSize(Size[] choices, int width, int height) {
        List<Size> bigEnough = new ArrayList<>();
        for (Size option : choices) {
            if (option.getHeight() == option.getWidth() * height / width && option.getWidth() >= width && option.getHeight() >= height) {
                bigEnough.add(option);
            }
        }
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else {
            return choices[0];
        }
    }

    static class CompareSizesByArea implements Comparator<Size> {
        @Override
        public int compare(Size lhs, Size rhs) {
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() - (long) rhs.getWidth() * rhs.getHeight());
        }
    }

    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        if (backgroundThread != null) {
            backgroundThread.quitSafely();
            try {
                backgroundThread.join();
                backgroundThread = null;
                backgroundHandler = null;
            } catch (InterruptedException e) {
                Log.e(TAG, "Interrupted while stopping background thread", e);
            }
        }
    }

    private void takePicture() {
        if (cameraDevice == null || cameraCaptureSession == null) {
            Toast.makeText(this, "Camera chưa sẵn sàng.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(imageReader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));
            CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull android.hardware.camera2.TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    createCameraPreviewSession();
                    runOnUiThread(this::updateGalleryThumbnail); // Ensure UI update is on the main thread
                }
                private void updateGalleryThumbnail() {
                    MainActivity.this.updateGalleryThumbnail();
                }
            };
            cameraCaptureSession.stopRepeating();
            cameraCaptureSession.capture(captureBuilder.build(), captureCallback, null);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Failed to capture picture", e);
        }
    }

    private final ImageReader.OnImageAvailableListener onImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            backgroundHandler.post(new ImageSaver(reader.acquireNextImage(), currentText, textSize, currentTextPosition));
        }
    };

    private class ImageSaver implements Runnable {
        private final Image mImage;
        private final String textToDraw;
        private final float fontSize;
        private final TextPosition position;

        ImageSaver(Image image, String text, float size, TextPosition pos) {
            mImage = image;
            textToDraw = text;
            fontSize = size;
            position = pos;
        }

        @Override
        public void run() {
            ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            mImage.close();

            if (bitmap == null) return;

            if (cameraFacing == CameraCharacteristics.LENS_FACING_FRONT) {
                Matrix matrix = new Matrix();
                matrix.postScale(-1, 1);
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            }

            Bitmap mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
            Canvas canvas = new Canvas(mutableBitmap);
            Paint paint = new Paint();
            paint.setColor(Color.WHITE);
            paint.setTextSize(fontSize);
            paint.setShadowLayer(5f, 0f, 0f, Color.BLACK);

            if (!textToDraw.isEmpty()) {
                // Drawing logic remains the same
                // ...
                canvas.drawText(textToDraw, 50, canvas.getHeight() - 50, paint);
            }

            saveBitmapToGallery(mutableBitmap);
            bitmap.recycle();
            mutableBitmap.recycle();
        }
    }

    private void saveBitmapToGallery(Bitmap bitmap) {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "BDAM_IMG_" + timeStamp + ".jpg";
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, imageFileName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/" + getString(R.string.app_name));
            values.put(MediaStore.Images.Media.IS_PENDING, 1);
        }

        ContentResolver resolver = getContentResolver();
        Uri uri = null;
        try {
            Uri collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
            uri = resolver.insert(collection, values);
            if (uri == null) throw new IOException("Failed to create new MediaStore record.");
            try (OutputStream fos = resolver.openOutputStream(uri)) {
                if (fos == null) throw new IOException("Failed to get OutputStream.");
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear();
                values.put(MediaStore.Images.Media.IS_PENDING, 0);
                resolver.update(uri, values, null, null);
            }
            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Ảnh đã lưu", Toast.LENGTH_SHORT).show());
        } catch (IOException e) {
            if (uri != null) resolver.delete(uri, null, null);
            Log.e(TAG, "Error saving image", e);
            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Lỗi khi lưu ảnh", Toast.LENGTH_SHORT).show());
        }
    }
}