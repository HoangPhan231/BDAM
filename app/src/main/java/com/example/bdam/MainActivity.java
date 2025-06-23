package com.example.bdam;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
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
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.util.TypedValue;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "BDAM_CameraApp";
    private static final int REQUEST_PERMISSIONS = 1;

    // --- Views ---
    private TextureView textureView;
    private EditText editTextTextToAdd;
    private ImageButton buttonCapture, buttonSettings, buttonFlash, buttonCameraSwitch, buttonGalleryThumbnail;
    private TextView textPhoto, textVideo, textOverlayDisplay;
    private View focusOverlay;
    private ConstraintLayout mainActivityLayout;

    // --- Camera2 API Variables ---
    private CameraDevice cameraDevice;
    private CameraCaptureSession cameraCaptureSession;
    private CaptureRequest.Builder previewCaptureRequestBuilder;
    private Size previewSize;
    private String cameraId;
    private int cameraFacing = CameraCharacteristics.LENS_FACING_FRONT;
    private ImageReader imageReader;
    private int flashMode = CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH;

    // --- Background Thread for Camera Operations ---
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    private Semaphore cameraOpenCloseLock = new Semaphore(1);

    // --- Handler để cập nhật overlay trực tiếp ---
    private Handler overlayUpdateHandler;
    private Runnable overlayUpdateRunnable;

    // --- Biến lưu cài đặt để tránh đọc lại SharedPreferences liên tục ---
    private String currentTextToDisplay = "";
    private String currentDateFormat = "EEEE, dd MMMM yyyy";
    private Set<String> currentTags = new HashSet<>();
    private float currentTextSize = 20f;
    private int currentTextColor = Color.WHITE;
    private TextPosition currentTextPosition = TextPosition.BOTTOM_RIGHT;


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
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            cameraOpenCloseLock.release();
            camera.close();
            cameraDevice = null;
            finish();
        }
    };

    private final TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
            openCamera(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {}

        @Override
        public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {}
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeViews();

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

        updateGalleryThumbnail();
    }

    private void initializeViews() {
        mainActivityLayout = findViewById(R.id.main_activity_layout);
        textureView = findViewById(R.id.textureView);
        editTextTextToAdd = findViewById(R.id.editText_textToAdd);
        buttonCapture = findViewById(R.id.button_capture);
        buttonSettings = findViewById(R.id.button_settings);
        buttonFlash = findViewById(R.id.button_flash_auto); // Sử dụng một nút duy nhất cho flash
        buttonCameraSwitch = findViewById(R.id.button_camera_switch);
        buttonGalleryThumbnail = findViewById(R.id.button_gallery_thumbnail);
        textPhoto = findViewById(R.id.text_photo);
        textVideo = findViewById(R.id.text_video);
        textOverlayDisplay = findViewById(R.id.text_overlay_display);
        focusOverlay = findViewById(R.id.focus_overlay);
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
        loadSettingsAndUpdateOverlay();
        updateGalleryThumbnail();
    }

    @Override
    protected void onPause() {
        closeCamera();
        stopBackgroundThread();
        if (overlayUpdateHandler != null) {
            overlayUpdateHandler.removeCallbacks(overlayUpdateRunnable);
        }
        super.onPause();
    }

    private boolean checkAndRequestPermissions() {
        List<String> permissionsNeeded = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.CAMERA);
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
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setupCameraTextureView();
                if (textureView.isAvailable()) {
                    openCamera(textureView.getWidth(), textureView.getHeight());
                }
            } else {
                Toast.makeText(this, "BDAM cần quyền truy cập camera để hoạt động.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void setupEventListeners() {
        buttonCapture.setOnClickListener(v -> takePicture());
        buttonSettings.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
        });

        buttonFlash.setOnClickListener(v -> toggleFlashMode());
        buttonCameraSwitch.setOnClickListener(v -> switchCamera());
        buttonGalleryThumbnail.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, GalleryActivity.class);
            startActivity(intent);
        });

        // Các listener khác...
    }

    private void toggleFlashMode() {
        switch (flashMode) {
            case CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH:
                flashMode = CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH;
                buttonFlash.setImageResource(R.drawable.ic_flash_on);
                break;
            case CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH:
                flashMode = CaptureRequest.CONTROL_AE_MODE_OFF;
                buttonFlash.setImageResource(R.drawable.ic_flash_off);
                break;
            case CaptureRequest.CONTROL_AE_MODE_OFF:
            default:
                flashMode = CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH;
                buttonFlash.setImageResource(R.drawable.ic_flash_auto);
                break;
        }
        // Áp dụng ngay chế độ flash mới vào preview
        try {
            previewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, flashMode);
            cameraCaptureSession.setRepeatingRequest(previewCaptureRequestBuilder.build(), null, backgroundHandler);
        } catch (CameraAccessException | IllegalStateException e) {
            Log.e(TAG, "Failed to set flash mode", e);
        }
    }

    private void updateGalleryThumbnail() {
        String[] projection = { MediaStore.Images.Media._ID, MediaStore.Images.Media.DATA };
        String sortOrder = MediaStore.Images.Media.DATE_ADDED + " DESC";
        Uri uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        try (android.database.Cursor cursor = getContentResolver().query(uri, projection, null, null, sortOrder + " LIMIT 1")) {
            if (cursor != null && cursor.moveToFirst()) {
                int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
                long id = cursor.getLong(idColumn);
                Uri imageUri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, String.valueOf(id));

                // Sử dụng Glide hoặc phương thức khác để load thumbnail hiệu quả hơn
                buttonGalleryThumbnail.setImageURI(imageUri);
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
            cameraId = manager.getCameraIdList()[cameraFacing];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map == null) return;

            previewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class), width, height);
            Size largestImageSize = Collections.max(Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)), new CompareSizesByArea());
            imageReader = ImageReader.newInstance(largestImageSize.getWidth(), largestImageSize.getHeight(), ImageFormat.JPEG, 2);
            imageReader.setOnImageAvailableListener(onImageAvailableListener, backgroundHandler);

            if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            manager.openCamera(cameraId, stateCallback, backgroundHandler);
        } catch (CameraAccessException | InterruptedException e) {
            Log.e(TAG, "Cannot access camera", e);
        }
    }

    private void switchCamera() {
        closeCamera();
        cameraFacing = (cameraFacing == CameraCharacteristics.LENS_FACING_BACK) ? CameraCharacteristics.LENS_FACING_FRONT : CameraCharacteristics.LENS_FACING_BACK;
        openCamera(textureView.getWidth(), textureView.getHeight());
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
                        previewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, flashMode);
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
        int w = width;
        int h = height;
        for (Size option : choices) {
            if (option.getHeight() == option.getWidth() * h / w && option.getWidth() >= w && option.getHeight() >= h) {
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
        if (cameraDevice == null || cameraCaptureSession == null) return;
        try {
            final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(imageReader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, flashMode);

            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));

            cameraCaptureSession.stopRepeating();
            cameraCaptureSession.capture(captureBuilder.build(), new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull android.hardware.camera2.TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    createCameraPreviewSession();
                    //runOnUiThread(MainActivity.this::updateGalleryThumbnail);
                }
            }, null);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Failed to capture picture", e);
        }
    }

    private final ImageReader.OnImageAvailableListener onImageAvailableListener = reader -> {
        backgroundHandler.post(new ImageSaver(reader.acquireLatestImage(), currentTextToDisplay, currentTextColor, currentTextSize, currentTextPosition, cameraFacing == CameraCharacteristics.LENS_FACING_FRONT, MainActivity.this));
    };

    private void loadSettingsAndUpdateOverlay() {
        SharedPreferences prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE);

        // 1. Tải các giá trị cài đặt
        currentTextColor = prefs.getInt(SettingsActivity.KEY_COLOR, Color.WHITE);
        currentTextSize = prefs.getFloat(SettingsActivity.KEY_TEXT_SIZE, 2f);
        currentDateFormat = prefs.getString(SettingsActivity.KEY_DATE_FORMAT, "EEEE, dd MMMM yyyy");
        currentTags = prefs.getStringSet(SettingsActivity.KEY_TAGS, new HashSet<>());
        String positionName = prefs.getString(SettingsActivity.KEY_POSITION, TextPosition.BOTTOM_RIGHT.name());
        currentTextPosition = TextPosition.valueOf(positionName);

        // 2. Áp dụng màu sắc và kích thước
        textOverlayDisplay.setTextColor(currentTextColor);
        textOverlayDisplay.setTextSize(TypedValue.COMPLEX_UNIT_SP, currentTextSize);

        // 3. Áp dụng vị trí
        applyTextPosition(currentTextPosition);

        // 4. Khởi tạo và bắt đầu Handler để cập nhật văn bản mỗi giây
        if (overlayUpdateHandler == null) {
            overlayUpdateHandler = new Handler(Looper.getMainLooper());
        } else {
            overlayUpdateHandler.removeCallbacks(overlayUpdateRunnable);
        }

        overlayUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                // Lấy định dạng ngày giờ đã chọn trong cài đặt
                SimpleDateFormat sdf = new SimpleDateFormat(currentDateFormat, Locale.getDefault());
                String dateTimeString = sdf.format(new Date());
                // Lấy danh sách các tag đã lưu
                StringBuilder tagsBuilder = new StringBuilder();
                if (currentTags != null && !currentTags.isEmpty()) {
                    for (String tag : currentTags) {
                        tagsBuilder.append("#").append(tag).append(" ");
                    }
                }
                // Kết hợp ngày giờ và các tag lại thành một chuỗi duy nhất
                // Đây là nơi quyết định cấu trúc cuối cùng của văn bản
                currentTextToDisplay = dateTimeString;
                if (tagsBuilder.length() > 0) {
                    currentTextToDisplay += "\n" + tagsBuilder.toString().trim();
                }
                // Gán chuỗi đã tạo vào TextView và Lặp lại sau 1 giây
                textOverlayDisplay.setText(currentTextToDisplay);
                overlayUpdateHandler.postDelayed(this, 1000);
            }
        };
        overlayUpdateHandler.post(overlayUpdateRunnable);
    }

    private void applyTextPosition(TextPosition position) {
        ConstraintSet constraintSet = new ConstraintSet();
        constraintSet.clone(mainActivityLayout);

        int margin = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics()
        );

        constraintSet.clear(R.id.text_overlay_display, ConstraintSet.TOP);
        constraintSet.clear(R.id.text_overlay_display, ConstraintSet.BOTTOM);
        constraintSet.clear(R.id.text_overlay_display, ConstraintSet.START);
        constraintSet.clear(R.id.text_overlay_display, ConstraintSet.END);

        switch (position) {
            case TOP_LEFT:
                constraintSet.connect(R.id.text_overlay_display, ConstraintSet.TOP, R.id.top_toolbar, ConstraintSet.BOTTOM, margin);
                constraintSet.connect(R.id.text_overlay_display, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, margin);
                break;
            case TOP_CENTER:
                constraintSet.connect(R.id.text_overlay_display, ConstraintSet.TOP, R.id.top_toolbar, ConstraintSet.BOTTOM, margin);
                constraintSet.connect(R.id.text_overlay_display, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START);
                constraintSet.connect(R.id.text_overlay_display, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END);
                break;
            case TOP_RIGHT:
                constraintSet.connect(R.id.text_overlay_display, ConstraintSet.TOP, R.id.top_toolbar, ConstraintSet.BOTTOM, margin);
                constraintSet.connect(R.id.text_overlay_display, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, margin);
                break;
            case BOTTOM_LEFT:
                constraintSet.connect(R.id.text_overlay_display, ConstraintSet.BOTTOM, R.id.bottom_control_panel, ConstraintSet.TOP, margin);
                constraintSet.connect(R.id.text_overlay_display, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, margin);
                break;
            case BOTTOM_CENTER:
                constraintSet.connect(R.id.text_overlay_display, ConstraintSet.BOTTOM, R.id.bottom_control_panel, ConstraintSet.TOP, margin);
                constraintSet.connect(R.id.text_overlay_display, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START);
                constraintSet.connect(R.id.text_overlay_display, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END);
                break;
            case CENTER:
                constraintSet.connect(R.id.text_overlay_display, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP);
                constraintSet.connect(R.id.text_overlay_display, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM);
                constraintSet.connect(R.id.text_overlay_display, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START);
                constraintSet.connect(R.id.text_overlay_display, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END);
                break;
            case BOTTOM_RIGHT:
            default:
                constraintSet.connect(R.id.text_overlay_display, ConstraintSet.BOTTOM, R.id.bottom_control_panel, ConstraintSet.TOP, margin);
                constraintSet.connect(R.id.text_overlay_display, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, margin);
                break;
        }
        constraintSet.applyTo(mainActivityLayout);
    }

    private static class ImageSaver implements Runnable {
        private final Image mImage;
        private final String mTextToDraw;
        private final int mTextColor;
        private final float mTextSize;
        private final TextPosition mPosition;
        private final boolean mIsFrontFacing;
        private final MainActivity  mActivity;

        ImageSaver(Image image, String text, int color, float size, TextPosition pos, boolean isFront, MainActivity activity) {
            mImage = image;
            mTextToDraw = text;
            mTextColor = color;
            mTextSize = size;
            mPosition = pos;
            mIsFrontFacing = isFront;
            mActivity  = activity;
        }

        @Override
        public void run() {
            ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            mImage.close();

            if (bitmap == null) return;

            // Xoay ảnh camera trước để không bị ngược
            /*if (mIsFrontFacing) {
                Matrix matrix = new Matrix();
                matrix.postScale(-1, 1, bitmap.getWidth() / 2f, bitmap.getHeight() / 2f);
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            }*/

            Bitmap mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
            Canvas canvas = new Canvas(mutableBitmap);

            // Cấu hình bút vẽ
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(mTextColor);
            float targetSizeInPixels = (float)canvas.getHeight() * (mTextSize / 500);
            paint.setTextSize(targetSizeInPixels);
            paint.setShadowLayer(5f, 0f, 0f, Color.BLACK);

            drawTextOnCanvas(canvas, paint);

            saveBitmapToGallery(mutableBitmap);
            bitmap.recycle();
            mutableBitmap.recycle();
        }

        private void drawTextOnCanvas(Canvas canvas, Paint paint) {
            if (mTextToDraw == null || mTextToDraw.isEmpty()) return;

            String[] lines = mTextToDraw.split("\n");
            float margin = 50f;
            float y;
            float totalTextHeight = (paint.descent() - paint.ascent()) * lines.length + paint.getFontSpacing() * (lines.length - 1);

            // Tính toán vị trí Y ban đầu
            switch (mPosition) {
                case TOP_LEFT:
                case TOP_CENTER:
                case TOP_RIGHT:
                    y = margin - paint.ascent();
                    break;
                case CENTER:
                    y = (canvas.getHeight() / 2f) - (totalTextHeight / 2f) - paint.ascent();
                    break;
                case BOTTOM_LEFT:
                case BOTTOM_CENTER:
                case BOTTOM_RIGHT:
                default:
                    y = canvas.getHeight() - margin - totalTextHeight + paint.getFontSpacing();
                    break;
            }

            // Vẽ từng dòng
            for (String line : lines) {
                Rect bounds = new Rect();
                paint.getTextBounds(line, 0, line.length(), bounds);
                float x;

                switch (mPosition) {
                    case TOP_LEFT:
                    case BOTTOM_LEFT:
                        paint.setTextAlign(Paint.Align.LEFT);
                        x = margin;
                        break;
                    case TOP_CENTER:
                    case BOTTOM_CENTER:
                    case CENTER:
                        paint.setTextAlign(Paint.Align.CENTER);
                        x = canvas.getWidth() / 2f;
                        break;
                    case TOP_RIGHT:
                    case BOTTOM_RIGHT:
                    default:
                        paint.setTextAlign(Paint.Align.RIGHT);
                        x = canvas.getWidth() - margin;
                        break;
                }
                canvas.drawText(line, x, y, paint);
                y += paint.getFontSpacing(); // Di chuyển xuống dòng tiếp theo
            }
        }

        // Trong file: MainActivity.java
// Bên trong lớp: ImageSaver

        private void saveBitmapToGallery(Bitmap bitmap) {
            // 1. Chuẩn bị thông tin cho ảnh
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String imageFileName = "BDAM_IMG_" + timeStamp + ".jpg";
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, imageFileName);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/" + mActivity.getString(R.string.app_name));
                values.put(MediaStore.Images.Media.IS_PENDING, 1);
            }

            ContentResolver resolver = mActivity.getContentResolver();
            Uri uri = null;

            try {
                // 2. Tạo record mới trong MediaStore và lấy về Uri
                Uri collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                uri = resolver.insert(collection, values);
                if (uri == null) {
                    throw new IOException("Failed to create new MediaStore record.");
                }

                // 3. Ghi dữ liệu ảnh vào Uri
                try (OutputStream fos = resolver.openOutputStream(uri)) {
                    if (fos == null) {
                        throw new IOException("Failed to get OutputStream.");
                    }
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
                }

                // 4. Báo hiệu ảnh đã sẵn sàng
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear();
                    values.put(MediaStore.Images.Media.IS_PENDING, 0);
                    resolver.update(uri, values, null, null);
                }

                // 5. Cập nhật UI với Uri của ảnh vừa lưu
                // Tạo biến final để có thể sử dụng trong lambda
                final Uri finalUri = uri;
                new Handler(Looper.getMainLooper()).post(() -> {
                    Toast.makeText(mActivity, "Ảnh đã lưu", Toast.LENGTH_SHORT).show();

                    // Dùng Glide để tải ảnh từ Uri và hiển thị lên ImageButton
                    // Đây là cách hiệu quả và chắc chắn nhất
                    com.bumptech.glide.Glide.with(mActivity)
                            .load(finalUri)
                            .centerCrop()
                            .into(mActivity.buttonGalleryThumbnail);
                });

            } catch (IOException e) {
                if (uri != null) {
                    resolver.delete(uri, null, null);
                }
                Log.e(TAG, "Error saving image", e);
                new Handler(Looper.getMainLooper()).post(() ->
                        Toast.makeText(mActivity, "Lỗi khi lưu ảnh", Toast.LENGTH_SHORT).show());
            }
        }
    }
}