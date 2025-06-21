package com.example.bdam;

import android.content.Context;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.RadioButton;
import android.widget.TextView;
import java.util.List;
import java.util.Map;

public class SizePickerAdapter extends BaseAdapter {

    private final Context context;
    private final List<Map.Entry<String, Float>> sizeList;
    private final LayoutInflater inflater;
    private float selectedSize;

    public SizePickerAdapter(Context context, List<Map.Entry<String, Float>> sizeList, float currentlySelectedSize) {
        this.context = context;
        this.sizeList = sizeList;
        this.selectedSize = currentlySelectedSize;
        this.inflater = LayoutInflater.from(context);
    }

    @Override
    public int getCount() {
        return sizeList.size();
    }

    @Override
    public Object getItem(int position) {
        return sizeList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.size_picker_item, parent, false);
            holder = new ViewHolder();
            holder.textSizePreview = convertView.findViewById(R.id.text_size_preview);
            holder.radioButton = convertView.findViewById(R.id.radio_button_selected);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        // Lấy dữ liệu size tại vị trí hiện tại
        Map.Entry<String, Float> sizeEntry = sizeList.get(position);
        String sizeName = sizeEntry.getKey();
        float sizeValue = sizeEntry.getValue();

        // Gán dữ liệu vào view
        holder.textSizePreview.setText(sizeName);
        holder.textSizePreview.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeValue);

        // Kiểm tra và đặt trạng thái cho RadioButton
        // So sánh float cần có một sai số nhỏ (epsilon)
        if (Math.abs(sizeValue - selectedSize) < 0.1f) {
            holder.radioButton.setChecked(true);
        } else {
            holder.radioButton.setChecked(false);
        }

        return convertView;
    }

    private static class ViewHolder {
        TextView textSizePreview;
        RadioButton radioButton;
    }
}