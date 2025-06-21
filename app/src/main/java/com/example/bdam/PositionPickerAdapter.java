package com.example.bdam;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.RadioButton;
import android.widget.TextView;
import java.util.List;

public class PositionPickerAdapter extends BaseAdapter {

    private final Context context;
    private final List<TextPosition> positionList;
    private final LayoutInflater inflater;
    private TextPosition selectedPosition;

    public PositionPickerAdapter(Context context, List<TextPosition> positionList, TextPosition currentlySelectedPosition) {
        this.context = context;
        this.positionList = positionList;
        this.selectedPosition = currentlySelectedPosition;
        this.inflater = LayoutInflater.from(context);
    }

    @Override
    public int getCount() {
        return positionList.size();
    }

    @Override
    public Object getItem(int position) {
        return positionList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.position_picker_item, parent, false);
            holder = new ViewHolder();
            holder.textPositionName = convertView.findViewById(R.id.text_position_name);
            holder.radioButton = convertView.findViewById(R.id.radio_button_selected_position);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        // Lấy dữ liệu vị trí
        TextPosition currentPosition = positionList.get(position);

        // Gán dữ liệu vào view
        holder.textPositionName.setText(currentPosition.getDisplayName());

        // Kiểm tra và đặt trạng thái cho RadioButton
        if (currentPosition == selectedPosition) {
            holder.radioButton.setChecked(true);
        } else {
            holder.radioButton.setChecked(false);
        }

        return convertView;
    }

    private static class ViewHolder {
        TextView textPositionName;
        RadioButton radioButton;
    }
}