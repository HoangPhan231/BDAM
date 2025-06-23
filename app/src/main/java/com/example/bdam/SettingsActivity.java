package com.example.bdam;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class SettingsActivity extends AppCompatActivity {

    // --- Views ---
    private Button buttonOk, buttonAddTag, buttonChangeTextOrder, buttonRestore, buttonAdvanced;
    private TextView settingFontColor, settingTextSize, settingPosition, settingDateTime, settingAddress;

    // --- Biến lưu trạng thái cài đặt ---
    private int mSelectedColor;
    private String mSelectedColorName = "White";
    private float mSelectedTextSize;
    private String mSelectedTextSizeName = "Medium";
    private TextPosition mSelectedPosition;
    private String mSelectedDateTimeFormat;
    private Handler mTimeHandler;
    private Runnable mTimeUpdateRunnable;

    // --- Hằng số cho SharedPreferences ---
    public static final String PREFS_NAME = "BDAM_Prefs";
    public static final String KEY_COLOR = "font_color";
    public static final String KEY_TEXT_SIZE = "text_size";
    public static final String KEY_POSITION = "text_position";
    public static final String KEY_DATE_FORMAT = "date_format";
    public static final String KEY_TAGS = "user_tags";


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        if (getSupportActionBar() != null) { getSupportActionBar().hide(); }

        initializeViews();
        loadExistingSettings();
        setupEventListeners();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mTimeHandler.post(mTimeUpdateRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mTimeHandler.removeCallbacks(mTimeUpdateRunnable);
    }

    private void initializeViews() {
        settingFontColor = findViewById(R.id.setting_font_color);
        settingTextSize = findViewById(R.id.setting_text_size);
        settingPosition = findViewById(R.id.setting_position);
        settingDateTime = findViewById(R.id.setting_date_time);
        settingAddress = findViewById(R.id.setting_address);

        buttonOk = findViewById(R.id.button_ok);
        buttonAddTag = findViewById(R.id.button_add_tag);
        buttonChangeTextOrder = findViewById(R.id.button_change_text_order);
        buttonRestore = findViewById(R.id.button_restore);
        buttonAdvanced = findViewById(R.id.button_advanced);
    }

    private void loadExistingSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        mSelectedColor = prefs.getInt(KEY_COLOR, ContextCompat.getColor(this, android.R.color.white));
        updateFontColorUI();

        mSelectedTextSize = prefs.getFloat(KEY_TEXT_SIZE, 20f);
        mSelectedTextSizeName = getTextSizeName(mSelectedTextSize);
        updateTextSizeUI();

        String positionName = prefs.getString(KEY_POSITION, TextPosition.BOTTOM_RIGHT.name());
        mSelectedPosition = TextPosition.valueOf(positionName);
        updatePositionUI();

        mSelectedDateTimeFormat = prefs.getString(KEY_DATE_FORMAT, "dd MMM yyyy HH:mm:ss");
        setupLiveTimeUpdater();
    }

    private String getTextSizeName(float size) {
        if (Math.abs(size - 12f) < 0.1) return "Tiny";
        if (Math.abs(size - 16f) < 0.1) return "Small";
        if (Math.abs(size - 28f) < 0.1) return "Big";
        if (Math.abs(size - 36f) < 0.1) return "Huge";
        return "Medium";
    }

    private void setupEventListeners() {
        settingFontColor.setOnClickListener(v -> showColorPickerDialog());
        settingTextSize.setOnClickListener(v -> showSizePickerDialog());
        settingPosition.setOnClickListener(v -> showPositionPickerDialog());
        settingDateTime.setOnClickListener(v -> showDateFormatPickerDialog());
        buttonAddTag.setOnClickListener(v -> showAddTagDialog());

        buttonOk.setOnClickListener(v -> {
            saveAllSettings();
            Toast.makeText(SettingsActivity.this, "Cài đặt đã được lưu.", Toast.LENGTH_SHORT).show();
            finish();
        });

        buttonRestore.setOnClickListener(v -> Toast.makeText(this, "Chức năng Reset chưa được triển khai.", Toast.LENGTH_SHORT).show());
        buttonAdvanced.setOnClickListener(v -> Toast.makeText(this, "Chức năng Advanced chưa được triển khai.", Toast.LENGTH_SHORT).show());
        buttonChangeTextOrder.setOnClickListener(v -> Toast.makeText(this, "Chức năng Change text order chưa được triển khai.", Toast.LENGTH_SHORT).show());
    }

    private void saveAllSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(KEY_COLOR, mSelectedColor);
        editor.putFloat(KEY_TEXT_SIZE, mSelectedTextSize);
        editor.putString(KEY_POSITION, mSelectedPosition.name());
        editor.putString(KEY_DATE_FORMAT, mSelectedDateTimeFormat);
        editor.apply();
    }

    // --- Các phương thức cho Font Color ---
    private void updateFontColorUI() {
        settingFontColor.setBackgroundColor(mSelectedColor);
        // Cần lấy tên màu từ giá trị màu, phần này cần cải tiến nếu muốn hiển thị tên chính xác
        // Tạm thời để trống hoặc giữ logic cũ
        settingFontColor.setText("Font Color");
        int red = android.graphics.Color.red(mSelectedColor);
        int green = android.graphics.Color.green(mSelectedColor);
        int blue = android.graphics.Color.blue(mSelectedColor);
        if ((red * 0.299 + green * 0.587 + blue * 0.114) > 186) {
            settingFontColor.setTextColor(ContextCompat.getColor(this, android.R.color.black));
        } else {
            settingFontColor.setTextColor(ContextCompat.getColor(this, android.R.color.white));
        }
    }

    private void showColorPickerDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Font Color");
        Map<String, Integer> colors = new LinkedHashMap<>();

        // *** ĐÂY LÀ PHẦN ĐÃ SỬA LỖI ***
        colors.put("White", ContextCompat.getColor(this, android.R.color.white));
        colors.put("Red", ContextCompat.getColor(this, R.color.picker_red));
        colors.put("Green", ContextCompat.getColor(this, R.color.picker_green));
        colors.put("Blue", ContextCompat.getColor(this, R.color.picker_blue));
        colors.put("Orange", ContextCompat.getColor(this, R.color.picker_orange));
        colors.put("Purple", ContextCompat.getColor(this, R.color.picker_purple));
        colors.put("Teal", ContextCompat.getColor(this, R.color.picker_teal));
        colors.put("Gray", ContextCompat.getColor(this, R.color.picker_gray));
        colors.put("Black", ContextCompat.getColor(this, android.R.color.black));

        List<Map.Entry<String, Integer>> colorEntries = new ArrayList<>(colors.entrySet());
        ListView listView = new ListView(this);
        ColorListAdapter adapter = new ColorListAdapter(this, colorEntries, mSelectedColor);
        listView.setAdapter(adapter);
        builder.setView(listView);
        final AlertDialog dialog = builder.create();

        listView.setOnItemClickListener((parent, view, position, id) -> {
            Map.Entry<String, Integer> selectedEntry = colorEntries.get(position);
            mSelectedColor = selectedEntry.getValue();
            mSelectedColorName = selectedEntry.getKey(); // Lưu tên màu để dùng nếu cần
            updateFontColorUI();
            dialog.dismiss();
        });

        builder.setNegativeButton("Cancel", (d, which) -> d.dismiss());
        dialog.show();
    }

    // --- Các phương thức cho Text Size ---
    private void updateTextSizeUI() {
        settingTextSize.setText("Text size (" + mSelectedTextSizeName + ")");
    }

    private void showSizePickerDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Text Size");

        Map<String, Float> sizes = new LinkedHashMap<>();
        sizes.put("Tiny", 12f);
        sizes.put("Small", 16f);
        sizes.put("Medium", 20f);
        sizes.put("Big", 28f);
        sizes.put("Huge", 36f);
        List<Map.Entry<String, Float>> sizeEntries = new ArrayList<>(sizes.entrySet());

        ListView listView = new ListView(this);
        SizePickerAdapter adapter = new SizePickerAdapter(this, sizeEntries, mSelectedTextSize);
        listView.setAdapter(adapter);
        builder.setView(listView);

        final AlertDialog dialog = builder.create();

        listView.setOnItemClickListener((parent, view, position, id) -> {
            Map.Entry<String, Float> selectedEntry = sizeEntries.get(position);
            mSelectedTextSizeName = selectedEntry.getKey();
            mSelectedTextSize = selectedEntry.getValue();
            updateTextSizeUI();
            dialog.dismiss();
        });

        builder.setNegativeButton("Cancel", null);
        dialog.show();
    }

    // --- Các phương thức cho Position ---
    private void updatePositionUI() {
        settingPosition.setText("Position (" + mSelectedPosition.getDisplayName() + ")");
    }

    private void showPositionPickerDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Position");
        List<TextPosition> positionList = new ArrayList<>(Arrays.asList(TextPosition.values()));

        ListView listView = new ListView(this);
        PositionPickerAdapter adapter = new PositionPickerAdapter(this, positionList, mSelectedPosition);
        listView.setAdapter(adapter);
        builder.setView(listView);

        final AlertDialog dialog = builder.create();

        listView.setOnItemClickListener((parent, view, position, id) -> {
            mSelectedPosition = positionList.get(position);
            updatePositionUI();
            dialog.dismiss();
        });

        builder.setNegativeButton("Cancel", null);
        dialog.show();
    }

    // --- Các phương thức cho Date Time ---
    private void setupLiveTimeUpdater() {
        mTimeHandler = new Handler(Looper.getMainLooper());
        mTimeUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                SimpleDateFormat sdf = new SimpleDateFormat(mSelectedDateTimeFormat, Locale.getDefault());
                settingDateTime.setText(sdf.format(new Date()));
                mTimeHandler.postDelayed(this, 1000);
            }
        };
    }

    private void showDateFormatPickerDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Date Time Format");

        List<String> formatPatterns = new ArrayList<>(Arrays.asList(
                "dd MMM yyyy HH:mm:ss",
                "dd/MM/yyyy HH:mm",
                "EEEE, dd MMMM yyyy",
                "HH:mm:ss dd/MM/yyyy",
                "dd/MM/yy HH:mm",
                "HH:mm",
                "dd/MM/yyyy HH:mm:ss.SSS"
        ));

        ListView listView = new ListView(this);
        DateFormatPickerAdapter adapter = new DateFormatPickerAdapter(this, formatPatterns, mSelectedDateTimeFormat);
        listView.setAdapter(adapter);
        builder.setView(listView);

        final AlertDialog dialog = builder.create();

        listView.setOnItemClickListener((parent, view, position, id) -> {
            mSelectedDateTimeFormat = formatPatterns.get(position);
            mTimeHandler.removeCallbacks(mTimeUpdateRunnable);
            mTimeHandler.post(mTimeUpdateRunnable);
            dialog.dismiss();
        });

        builder.setNegativeButton("Cancel", null);
        dialog.show();
    }

    // --- Các phương thức cho Add Tag ---
    private void showAddTagDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = this.getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_add_tag, null);
        builder.setView(dialogView);

        final ChipGroup chipGroup = dialogView.findViewById(R.id.chip_group_tags);
        final EditText editTextTagInput = dialogView.findViewById(R.id.edit_text_tag_input);
        final Button buttonAddNewTag = dialogView.findViewById(R.id.button_add_new_tag);
        final Button buttonDeleteAll = dialogView.findViewById(R.id.button_delete_all);
        final Button buttonImport = dialogView.findViewById(R.id.button_import);
        final Button buttonOkTag = dialogView.findViewById(R.id.button_ok_tag);

        loadTagsToChipGroup(chipGroup);

        final AlertDialog dialog = builder.create();

        buttonAddNewTag.setOnClickListener(v -> {
            String tagText = editTextTagInput.getText().toString().trim();
            if (!tagText.isEmpty()) {
                addChipToGroup(tagText, chipGroup);
                editTextTagInput.setText("");
            } else {
                Toast.makeText(SettingsActivity.this, "Tag không được để trống.", Toast.LENGTH_SHORT).show();
            }
        });

        buttonDeleteAll.setOnClickListener(v -> {
            chipGroup.removeAllViews();
            Toast.makeText(SettingsActivity.this, "Đã xóa tất cả tag.", Toast.LENGTH_SHORT).show();
        });

        buttonImport.setOnClickListener(v -> Toast.makeText(SettingsActivity.this, "Chức năng Import chưa được triển khai.", Toast.LENGTH_SHORT).show());

        buttonOkTag.setOnClickListener(v -> {
            saveTagsFromChipGroup(chipGroup);
            Toast.makeText(SettingsActivity.this, "Đã lưu các tag!", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        dialog.show();
    }

    private void addChipToGroup(String text, ChipGroup chipGroup) {
        Chip chip = new Chip(this);
        chip.setText(text);
        chip.setCloseIconVisible(true);
        chip.setClickable(true);
        chip.setCheckable(false);
        chip.setOnCloseIconClickListener(v -> chipGroup.removeView(chip));
        chipGroup.addView(chip);
    }

    private void loadTagsToChipGroup(ChipGroup chipGroup) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> tags = prefs.getStringSet(KEY_TAGS, new HashSet<>());
        chipGroup.removeAllViews();
        for (String tag : tags) {
            addChipToGroup(tag, chipGroup);
        }
    }

    private void saveTagsFromChipGroup(ChipGroup chipGroup) {
        Set<String> tagsToSave = new HashSet<>();
        for (int i = 0; i < chipGroup.getChildCount(); i++) {
            Chip chip = (Chip) chipGroup.getChildAt(i);
            tagsToSave.add(chip.getText().toString());
        }
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putStringSet(KEY_TAGS, tagsToSave);
        editor.apply();
    }
}