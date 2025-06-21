package com.example.bdam;

import android.app.AlertDialog;
import android.os.Bundle;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SettingsActivity extends AppCompatActivity {

    private Button buttonOk, buttonAddTag;
    private TextView settingFontColor, settingTextSize, settingPosition; // Thêm settingPosition

    // Biến cho Font Color
    private int mSelectedColor;
    private String mSelectedColorName = "White";

    // Biến cho Text Size
    private float mSelectedTextSize;
    private String mSelectedTextSizeName = "Medium";

    // Biến mới cho Position
    private TextPosition mSelectedPosition;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        // --- Thiết lập Font Color ---
        mSelectedColor = ContextCompat.getColor(this, R.color.white);
        settingFontColor = findViewById(R.id.setting_font_color);
        updateFontColorUI();
        settingFontColor.setOnClickListener(v -> showColorPickerDialog());

        // --- Thiết lập Text Size ---
        mSelectedTextSize = 20f;
        settingTextSize = findViewById(R.id.setting_text_size);
        updateTextSizeUI();
        settingTextSize.setOnClickListener(v -> showSizePickerDialog());

        // --- Thiết lập Position ---
        mSelectedPosition = TextPosition.BOTTOM_RIGHT; // Vị trí mặc định
        settingPosition = findViewById(R.id.setting_position);
        updatePositionUI();
        settingPosition.setOnClickListener(v -> showPositionPickerDialog());


        // --- Các nút khác ---
        buttonOk = findViewById(R.id.button_ok);
        buttonAddTag = findViewById(R.id.button_add_tag);
        buttonOk.setOnClickListener(v -> {
            Toast.makeText(SettingsActivity.this, "Cài đặt đã lưu.", Toast.LENGTH_SHORT).show();
            finish();
        });
        buttonAddTag.setOnClickListener(v -> showAddTagDialog());
    }

    // ... (Các phương thức cũ cho color và size)

    // --- Các phương thức mới cho Position ---
    private void updatePositionUI() {
        settingPosition.setText("Position (" + mSelectedPosition.getDisplayName() + ")");
    }

    private void showPositionPickerDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Position");

        // Lấy danh sách tất cả các giá trị của Enum
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


    // --- Các phương thức đã có ---
    private void updateFontColorUI() {
        settingFontColor.setBackgroundColor(mSelectedColor);
        settingFontColor.setText("Font Color (" + mSelectedColorName + ")");
        int red = android.graphics.Color.red(mSelectedColor);
        int green = android.graphics.Color.green(mSelectedColor);
        int blue = android.graphics.Color.blue(mSelectedColor);
        if ((red * 0.299 + green * 0.587 + blue * 0.114) > 186) {
            settingFontColor.setTextColor(ContextCompat.getColor(this, R.color.black));
        } else {
            settingFontColor.setTextColor(ContextCompat.getColor(this, R.color.white));
        }
    }

    private void showColorPickerDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Font Color");
        Map<String, Integer> colors = new LinkedHashMap<>();
        colors.put("White", ContextCompat.getColor(this, R.color.white));
        colors.put("Red", ContextCompat.getColor(this, R.color.picker_red));
        colors.put("Green", ContextCompat.getColor(this, R.color.picker_green));
        colors.put("Blue", ContextCompat.getColor(this, R.color.picker_blue));
        colors.put("Orange", ContextCompat.getColor(this, R.color.picker_orange));
        colors.put("Purple", ContextCompat.getColor(this, R.color.picker_purple));
        colors.put("Teal", ContextCompat.getColor(this, R.color.picker_teal));
        colors.put("Gray", ContextCompat.getColor(this, R.color.picker_gray));
        colors.put("Black", ContextCompat.getColor(this, R.color.black));
        List<Map.Entry<String, Integer>> colorEntries = new ArrayList<>(colors.entrySet());
        ListView listView = new ListView(this);
        ColorListAdapter adapter = new ColorListAdapter(this, colorEntries, mSelectedColor);
        listView.setAdapter(adapter);
        builder.setView(listView);
        final AlertDialog dialog = builder.create();
        listView.setOnItemClickListener((parent, view, position, id) -> {
            Map.Entry<String, Integer> selectedEntry = colorEntries.get(position);
            mSelectedColor = selectedEntry.getValue();
            mSelectedColorName = selectedEntry.getKey();
            updateFontColorUI();
            dialog.dismiss();
        });
        builder.setNegativeButton("Cancel", (d, which) -> d.dismiss());
        dialog.show();
    }

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

    private void showAddTagDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = this.getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_add_tag, null);
        builder.setView(dialogView);
        final EditText editTextAddTagInput = dialogView.findViewById(R.id.editText_add_tag_input);
        Button buttonDeleteAll = dialogView.findViewById(R.id.button_delete_all);
        Button buttonImport = dialogView.findViewById(R.id.button_import);
        Button buttonOkTag = dialogView.findViewById(R.id.button_ok_tag);
        final AlertDialog dialog = builder.create();
        buttonDeleteAll.setOnClickListener(v -> {
            editTextAddTagInput.setText("");
            Toast.makeText(SettingsActivity.this, "Đã xóa tất cả tags.", Toast.LENGTH_SHORT).show();
        });
        buttonImport.setOnClickListener(v -> Toast.makeText(SettingsActivity.this, "Chức năng Import tags chưa được triển khai.", Toast.LENGTH_SHORT).show());
        buttonOkTag.setOnClickListener(v -> {
            String tags = editTextAddTagInput.getText().toString();
            Toast.makeText(SettingsActivity.this, "Tags đã lưu (ví dụ): " + tags, Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });
        dialog.show();
    }
}