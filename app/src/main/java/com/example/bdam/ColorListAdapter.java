package com.example.bdam;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import java.util.List;
import java.util.Map;

public class ColorListAdapter extends BaseAdapter {

    private final Context context;
    private final List<Map.Entry<String, Integer>> colorList;
    private final LayoutInflater inflater;
    private int selectedColor;

    public ColorListAdapter(Context context, List<Map.Entry<String, Integer>> colorList, int currentlySelectedColor) {
        this.context = context;
        this.colorList = colorList;
        this.selectedColor = currentlySelectedColor;
        this.inflater = LayoutInflater.from(context);
    }

    @Override
    public int getCount() {
        return colorList.size();
    }

    @Override
    public Object getItem(int position) {
        return colorList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;

        if (convertView == null) {
            convertView = inflater.inflate(R.layout.color_picker_item, parent, false);
            holder = new ViewHolder();
            holder.colorSwatch = convertView.findViewById(R.id.color_swatch);
            holder.colorName = convertView.findViewById(R.id.color_name);
            holder.selectedIndicator = convertView.findViewById(R.id.image_view_selected);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        // Lấy dữ liệu màu tại vị trí hiện tại
        Map.Entry<String, Integer> colorEntry = colorList.get(position);
        String colorName = colorEntry.getKey();
        int colorValue = colorEntry.getValue();

        // Gán dữ liệu vào view
        holder.colorName.setText(colorName);
        holder.colorSwatch.setBackgroundColor(colorValue);

        // Hiển thị hoặc ẩn dấu tick lựa chọn
        if (colorValue == selectedColor) {
            holder.selectedIndicator.setVisibility(View.VISIBLE);
        } else {
            holder.selectedIndicator.setVisibility(View.GONE);
        }

        return convertView;
    }

    // Lớp ViewHolder để tối ưu hiệu suất cho ListView
    private static class ViewHolder {
        View colorSwatch;
        TextView colorName;
        ImageView selectedIndicator; //hello
    }
}