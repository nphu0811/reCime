package com.example.recime.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;
import com.example.recime.R;
import com.example.recime.model.BoardRow;
import java.util.List;

/** ListView adapter cho lịch tuần và giỏ đi chợ. */
public class BoardAdapter extends BaseAdapter {
  private final Context context;
  private final int layout;
  private final List<BoardRow> rowList;
  private final boolean planner;
  private final CompoundButton.OnCheckedChangeListener checkedListener;

  public BoardAdapter(
      Context context,
      int layout,
      List<BoardRow> rowList,
      boolean planner,
      CompoundButton.OnCheckedChangeListener checkedListener) {
    this.context = context;
    this.layout = layout;
    this.rowList = rowList;
    this.planner = planner;
    this.checkedListener = checkedListener;
  }

  @Override
  public int getCount() {
    return rowList.size();
  }

  @Override
  public BoardRow getItem(int position) {
    return rowList.get(position);
  }

  @Override
  public long getItemId(int position) {
    return position;
  }

  @Override
  public View getView(int position, View convertView, ViewGroup parent) {
    if (convertView == null) {
      convertView = LayoutInflater.from(context).inflate(layout, parent, false);
    }
    TextView title = convertView.findViewById(R.id.row_title);
    TextView meta = convertView.findViewById(R.id.row_meta);
    CheckBox check = convertView.findViewById(R.id.checked);
    BoardRow row = getItem(position);
    title.setText(row.title);
    meta.setText(row.meta);
    // Bỏ listener cũ trước khi bind lại view để không ghi nhầm checkbox đã tái sử dụng.
    check.setOnCheckedChangeListener(null);
    check.setVisibility(planner ? View.GONE : View.VISIBLE);
    check.setFocusable(false);
    check.setTag(row.grocery);
    check.setChecked(row.grocery != null && row.grocery.checked);
    check.setOnCheckedChangeListener(checkedListener);
    return convertView;
  }
}
