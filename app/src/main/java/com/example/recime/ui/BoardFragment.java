package com.example.recime.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.*;
import com.example.recime.*;
import com.example.recime.data.*;
import com.example.recime.domain.*;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.text.SimpleDateFormat;
import java.util.*;

public class BoardFragment extends Fragment {
  private boolean planner;
  private LabViewModel vm;
  private View root;
  private final Calendar week = Calendar.getInstance();
  private List<Recipe> recipes = new ArrayList<>();
  private List<Meal> meals = new ArrayList<>();
  private List<Grocery> groceries = new ArrayList<>();
  private final List<Row> rows = new ArrayList<>();
  private Rows adapter;

  public BoardFragment() {
    super(R.layout.fragment_board);
  }

  public static BoardFragment create(boolean planner) {
    BoardFragment f = new BoardFragment();
    Bundle b = new Bundle();
    b.putBoolean("planner", planner);
    f.setArguments(b);
    return f;
  }

  private static String day(Calendar c) {
    return new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(c.getTime());
  }

  @Override
  public void onViewCreated(View v, Bundle state) {
    root = v;
    planner = requireArguments().getBoolean("planner");
    vm = new ViewModelProvider(requireActivity()).get(LabViewModel.class);
    week.set(Calendar.HOUR_OF_DAY, 12);
    week.set(Calendar.MINUTE, 0);
    week.set(Calendar.SECOND, 0);
    week.add(Calendar.DAY_OF_MONTH, -((week.get(Calendar.DAY_OF_WEEK) + 5) % 7));
    if (state != null) week.setTimeInMillis(state.getLong("week", week.getTimeInMillis()));
    Ui.set(v, R.id.board_title, planner ? "Một tuần ngon lành" : "Giỏ đi chợ");
    Ui.set(
        v,
        R.id.board_subtitle,
        planner
            ? "Chọn từng bữa để thêm hoặc thay món. Nhấn giữ để bỏ món."
            : "Nguyên liệu cùng tên và đơn vị tương thích được gộp. Chạm để sửa; nhấn giữ để xóa.");
    v.findViewById(R.id.week_controls).setVisibility(planner ? View.VISIBLE : View.GONE);
    Ui.set(v, R.id.board_action, planner ? "Tạo giỏ từ tuần này" : "+ Thêm nguyên liệu");
    Ui.set(v, R.id.board_secondary, planner ? "Hôm nay / tuần hiện tại" : "Chia sẻ checklist");
    v.findViewById(R.id.prev_week)
        .setOnClickListener(
            x -> {
              week.add(Calendar.DAY_OF_MONTH, -7);
              render();
            });
    v.findViewById(R.id.next_week)
        .setOnClickListener(
            x -> {
              week.add(Calendar.DAY_OF_MONTH, 7);
              render();
            });
    v.findViewById(R.id.board_action)
        .setOnClickListener(
            x -> {
              if (planner) planGroceries();
              else groceryEditor(null);
            });
    v.findViewById(R.id.board_secondary)
        .setOnClickListener(
            x -> {
              if (planner) {
                week.setTimeInMillis(System.currentTimeMillis());
                week.add(Calendar.DAY_OF_MONTH, -((week.get(Calendar.DAY_OF_WEEK) + 5) % 7));
                render();
              } else share();
            });
    RecyclerView list = v.findViewById(R.id.board_list);
    list.setLayoutManager(new LinearLayoutManager(requireContext()));
    adapter = new Rows();
    list.setAdapter(adapter);
    vm.recipes.observe(
        getViewLifecycleOwner(),
        r -> {
          recipes = r;
          render();
        });
    vm.meals.observe(
        getViewLifecycleOwner(),
        m -> {
          meals = m;
          render();
        });
    vm.groceries.observe(
        getViewLifecycleOwner(),
        g -> {
          groceries = g;
          render();
        });
    render();
  }

  private Recipe recipe(long id) {
    for (Recipe r : recipes) if (r.id == id) return r;
    return null;
  }

  private void render() {
    if (root == null) return;
    rows.clear();
    if (planner) {
      Calendar end = (Calendar) week.clone();
      end.add(Calendar.DAY_OF_MONTH, 6);
      Ui.set(root, R.id.week_label, day(week) + "\n→ " + day(end));
      Calendar d = (Calendar) week.clone();
      String[] types = {"Sáng", "Trưa", "Tối"};
      for (int n = 0; n < 7; n++, d.add(Calendar.DAY_OF_MONTH, 1))
        for (String type : types) {
          Row row = new Row();
          row.day = day(d);
          row.type = type;
          for (Meal m : meals)
            if (m.day.equals(row.day) && m.type.equals(type)) {
              row.meal = m;
              break;
            }
          Recipe r = row.meal == null ? null : recipe(row.meal.recipeId);
          row.title = r == null ? "+ Chọn món" : r.title;
          row.meta =
              new SimpleDateFormat("EEE dd/MM", new Locale("vi")).format(d.getTime())
                  + " · "
                  + type;
          rows.add(row);
        }
      Ui.set(root, R.id.board_empty, "21 ô bữa ăn · dữ liệu lưu offline");
    } else {
      for (Grocery g : groceries) {
        Row row = new Row();
        row.grocery = g;
        row.title = g.name;
        row.meta =
            g.category
                + " · "
                + (g.amount == null ? "tùy lượng" : Units.format(g.amount) + " " + g.unit);
        rows.add(row);
      }
      Ui.set(
          root,
          R.id.board_empty,
          groceries.isEmpty()
              ? "Giỏ trống. Thêm từ công thức, lịch tuần hoặc nhập tay."
              : groceries.size()
                  + " nguyên liệu · "
                  + groceries.stream().filter(g -> g.checked).count()
                  + " đã mua");
    }
    adapter.notifyDataSetChanged();
  }

  private void selectMeal(Row row) {
    if (recipes.isEmpty()) {
      android.widget.Toast.makeText(
              requireContext(), "Hãy lưu công thức trước", android.widget.Toast.LENGTH_SHORT)
          .show();
      return;
    }
    String[] names = new String[recipes.size() + 1];
    names[0] = "Bỏ món khỏi bữa này";
    for (int i = 0; i < recipes.size(); i++) names[i + 1] = recipes.get(i).title;
    new MaterialAlertDialogBuilder(requireContext())
        .setTitle(row.day + " · " + row.type)
        .setItems(
            names,
            (d, w) -> {
              if (w == 0) {
                if (row.meal != null)
                  vm.repository.run(() -> vm.repository.dao.deleteMeal(row.meal));
              } else {
                Meal m = new Meal();
                m.day = row.day;
                m.type = row.type;
                m.slot = row.day + "|" + row.type;
                m.recipeId = recipes.get(w - 1).id;
                vm.repository.run(() -> vm.repository.dao.putMeal(m));
              }
            })
        .show();
  }

  private void planGroceries() {
    Calendar end = (Calendar) week.clone();
    end.add(Calendar.DAY_OF_MONTH, 6);
    List<Ingredient> values = new ArrayList<>();
    for (Meal m : meals)
      if (m.day.compareTo(day(week)) >= 0 && m.day.compareTo(day(end)) <= 0) {
        Recipe r = recipe(m.recipeId);
        if (r != null) values.addAll(RecipeParser.ingredients(r.ingredients));
      }
    if (values.isEmpty()) {
      new MaterialAlertDialogBuilder(requireContext())
          .setMessage("Tuần này chưa có món ăn.")
          .setPositiveButton("Đóng", null)
          .show();
      return;
    }
    new MaterialAlertDialogBuilder(requireContext())
        .setTitle("Thêm nguyên liệu của tuần?")
        .setMessage(
            "Thao tác sẽ cộng vào giỏ hiện tại theo khẩu phần gốc. Bấm lại sẽ cộng thêm lần nữa.")
        .setNegativeButton("Hủy", null)
        .setPositiveButton(
            "Thêm",
            (d, w) ->
                vm.repository.addIngredients(
                    values,
                    Ui.tsp(requireContext()),
                    Ui.tbsp(requireContext()),
                    Ui.cup(requireContext())))
        .show();
  }

  private void groceryEditor(Grocery original) {
    LinearLayout l = new LinearLayout(requireContext());
    l.setOrientation(LinearLayout.VERTICAL);
    l.setPadding(36, 0, 36, 0);
    EditText name =
        Ui.field(requireContext(), l, "Tên nguyên liệu *", original == null ? "" : original.name);
    EditText amount =
        Ui.field(
            requireContext(),
            l,
            "Số lượng (có thể trống)",
            original == null || original.amount == null ? "" : Units.format(original.amount));
    amount.setInputType(8194);
    EditText unit =
        Ui.field(
            requireContext(), l, "Đơn vị: g, kg, ml, quả…", original == null ? "" : original.unit);
    EditText group =
        Ui.field(
            requireContext(),
            l,
            "Nhóm hàng (tự phân loại nếu trống)",
            original == null ? "" : original.category);
    androidx.appcompat.app.AlertDialog dialog =
        new MaterialAlertDialogBuilder(requireContext())
            .setTitle(original == null ? "Thêm nguyên liệu" : "Sửa nguyên liệu")
            .setView(l)
            .setNegativeButton("Hủy", null)
            .setPositiveButton("Lưu", null)
            .create();
    dialog.setOnShowListener(
        d ->
            dialog
                .getButton(-1)
                .setOnClickListener(
                    x -> {
                      try {
                        if (name.getText().toString().trim().isEmpty())
                          throw new IllegalArgumentException();
                        Double value =
                            amount.getText().toString().trim().isEmpty()
                                ? null
                                : Double.valueOf(amount.getText().toString().replace(',', '.'));
                        if (value != null && (!Double.isFinite(value) || value < 0))
                          throw new IllegalArgumentException();
                        Grocery g = original == null ? new Grocery() : original;
                        g.name = name.getText().toString().trim();
                        g.amount = value;
                        g.unit = Units.canonical(unit.getText().toString());
                        g.category = group.getText().toString().trim();
                        if (g.category.isEmpty()) g.category = GroceryAggregator.category(g.name);
                        vm.repository.run(
                            () -> {
                              if (g.id == 0) vm.repository.dao.insertGrocery(g);
                              else vm.repository.dao.updateGrocery(g);
                            });
                        dialog.dismiss();
                      } catch (Exception e) {
                        name.setError("Tên không trống; số lượng không âm hợp lệ");
                      }
                    }));
    dialog.show();
  }

  private void share() {
    StringBuilder b = new StringBuilder("MyRecipeLab · Danh sách đi chợ\n");
    for (Grocery g : groceries)
      b.append(g.checked ? "[x] " : "[ ] ")
          .append(g.amount == null ? "" : Units.format(g.amount) + " " + g.unit + " ")
          .append(g.name)
          .append('\n');
    Intent intent = new Intent(Intent.ACTION_SEND);
    intent.setType("text/plain");
    intent.putExtra(Intent.EXTRA_TEXT, b.toString());
    startActivity(Intent.createChooser(intent, "Chia sẻ checklist"));
  }

  private static class Row {
    String title, meta, day, type;
    Meal meal;
    Grocery grocery;
  }

  class Rows extends RecyclerView.Adapter<Holder> {
    public Holder onCreateViewHolder(ViewGroup p, int t) {
      return new Holder(LayoutInflater.from(p.getContext()).inflate(R.layout.item_board, p, false));
    }

    public int getItemCount() {
      return rows.size();
    }

    public void onBindViewHolder(Holder h, int pos) {
      Row r = rows.get(pos);
      Ui.set(h.itemView, R.id.row_title, r.title);
      Ui.set(h.itemView, R.id.row_meta, r.meta);
      CheckBox check = h.itemView.findViewById(R.id.checked);
      check.setOnCheckedChangeListener(null);
      check.setVisibility(planner ? View.GONE : View.VISIBLE);
      check.setChecked(r.grocery != null && r.grocery.checked);
      check.setOnCheckedChangeListener(
          (b, c) -> {
            if (r.grocery != null) {
              r.grocery.checked = c;
              vm.repository.run(() -> vm.repository.dao.updateGrocery(r.grocery));
            }
          });
      h.itemView.setOnClickListener(
          x -> {
            if (planner) selectMeal(r);
            else groceryEditor(r.grocery);
          });
      h.itemView.setOnLongClickListener(
          x -> {
            if (planner) {
              if (r.meal != null)
                new MaterialAlertDialogBuilder(requireContext())
                    .setMessage("Bỏ món khỏi bữa này?")
                    .setNegativeButton("Hủy", null)
                    .setPositiveButton(
                        "Bỏ món",
                        (d, w) -> vm.repository.run(() -> vm.repository.dao.deleteMeal(r.meal)))
                    .show();
            } else
              new MaterialAlertDialogBuilder(requireContext())
                  .setMessage("Xóa " + r.grocery.name + "?")
                  .setNegativeButton("Hủy", null)
                  .setPositiveButton(
                      "Xóa",
                      (d, w) -> vm.repository.run(() -> vm.repository.dao.deleteGrocery(r.grocery)))
                  .show();
            return true;
          });
    }
  }

  static class Holder extends RecyclerView.ViewHolder {
    Holder(View v) {
      super(v);
    }
  }

  @Override
  public void onSaveInstanceState(Bundle b) {
    super.onSaveInstanceState(b);
    b.putLong("week", week.getTimeInMillis());
  }

  @Override
  public void onDestroyView() {
    root = null;
    super.onDestroyView();
  }
}
