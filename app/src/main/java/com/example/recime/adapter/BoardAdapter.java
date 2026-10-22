package com.example.recime.adapter;

import android.view.*;
import android.widget.CheckBox;
import androidx.recyclerview.widget.RecyclerView;
import com.example.recime.R;
import com.example.recime.helper.Ui;
import com.example.recime.model.BoardRow;
import java.util.List;

public class BoardAdapter extends RecyclerView.Adapter<BoardAdapter.Holder> {
  public interface Listener {
    void onClick(BoardRow row);

    void onLongClick(BoardRow row);

    void onChecked(BoardRow row, boolean checked);
  }

  private final List<BoardRow> rows;
  private final boolean planner;
  private final Listener listener;

  public BoardAdapter(List<BoardRow> rows, boolean planner, Listener listener) {
    this.rows = rows;
    this.planner = planner;
    this.listener = listener;
  }

  @Override
  public Holder onCreateViewHolder(ViewGroup parent, int type) {
    return new Holder(
        LayoutInflater.from(parent.getContext()).inflate(R.layout.item_board, parent, false));
  }

  @Override
  public int getItemCount() {
    return rows.size();
  }

  @Override
  public void onBindViewHolder(Holder holder, int position) {
    BoardRow row = rows.get(position);
    Ui.set(holder.itemView, R.id.row_title, row.title);
    Ui.set(holder.itemView, R.id.row_meta, row.meta);
    CheckBox check = holder.itemView.findViewById(R.id.checked);
    check.setOnCheckedChangeListener(null);
    check.setVisibility(planner ? View.GONE : View.VISIBLE);
    check.setChecked(row.grocery != null && row.grocery.checked);
    check.setOnCheckedChangeListener((button, checked) -> listener.onChecked(row, checked));
    holder.itemView.setOnClickListener(view -> listener.onClick(row));
    holder.itemView.setOnLongClickListener(
        view -> {
          listener.onLongClick(row);
          return true;
        });
  }

  static class Holder extends RecyclerView.ViewHolder {
    Holder(View view) {
      super(view);
    }
  }
}
