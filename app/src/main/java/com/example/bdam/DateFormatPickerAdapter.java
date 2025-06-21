package com.example.bdam;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.RadioButton;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DateFormatPickerAdapter extends BaseAdapter {

    private final Context context;
    private final List<Map.Entry<String, String>> formatList;
    private final LayoutInflater inflater;
    private String selectedFormatPattern;

    public DateFormatPickerAdapter(Context context, List<Map.Entry<String, String>> formatList, String currentlySelectedPattern) {
        this.context = context;
        this.formatList = formatList;
        this.selectedFormatPattern = currentlySelectedPattern;
        this.inflater = LayoutInflater.from(context);
    }

    @Override
    public int getCount() {
        return formatList.size();
    }

    @Override
    public Object getItem(int position) {
        return formatList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.date_format_picker_item, parent, false);
            holder = new ViewHolder();
            holder.textDateFormatPreview = convertView.findViewById(R.id.text_date_format_preview);
            holder.radioButton = convertView.findViewById(R.id.radio_button_selected_format);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        Map.Entry<String, String> formatEntry = formatList.get(position);
        String formatPattern = formatEntry.getValue();

        // Hiển thị ví dụ với thời gian hiện tại
        SimpleDateFormat sdf = new SimpleDateFormat(formatPattern, Locale.getDefault());
        holder.textDateFormatPreview.setText(sdf.format(new Date()));

        // Kiểm tra và đặt trạng thái cho RadioButton
        if (formatPattern.equals(selectedFormatPattern)) {
            holder.radioButton.setChecked(true);
        } else {
            holder.radioButton.setChecked(false);
        }

        return convertView;
    }

    private static class ViewHolder {
        TextView textDateFormatPreview;
        RadioButton radioButton;
    }
}