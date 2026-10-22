package com.example.recime.fragment;

import com.example.recime.helper.Motion;

import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.example.recime.*;
import com.example.recime.adapter.BoardAdapter;
import com.example.recime.helper.GroceryAggregator;
import com.example.recime.helper.RecipeParser;
import com.example.recime.helper.Ui;
import com.example.recime.helper.Units;
import com.example.recime.model.BoardRow;
import com.example.recime.model.Grocery;
import com.example.recime.model.Ingredient;
import com.example.recime.model.Meal;
import com.example.recime.model.Recipe;
import com.example.recime.viewmodel.LabViewModel;
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
  private final List<BoardRow> rows = new ArrayList<>();
  private BoardAdapter adapter;

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
    Motion.enter(v);
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
            ? "Thêm nhiều món cho mỗi bữa; nguyên liệu tự đồng bộ vào giỏ."
            : "Nguyên liệu theo lịch ăn tự cập nhật; món thêm tay giữ riêng.");
    v.findViewById(R.id.week_controls).setVisibility(planner ? View.VISIBLE : View.GONE);
    Ui.set(v, R.id.board_action, planner ? "Xem giỏ đồng bộ tự động ↗" : "+ Thêm nguyên liệu");
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
    ListView list = v.findViewById(R.id.board_list);
    adapter =
        new BoardAdapter(
            requireContext(),
            R.layout.item_board,
            rows,
            planner,
            new CompoundButton.OnCheckedChangeListener() {
              @Override
              public void onCheckedChanged(CompoundButton button, boolean checked) {
                Grocery grocery = (Grocery) button.getTag();
                if (grocery != null) {
                  grocery.checked = checked;
                  vm.repository.run(() -> vm.repository.dao.updateGrocery(grocery));
                }
              }
            });
    list.setAdapter(adapter);
    list.setOnItemClickListener(
        new AdapterView.OnItemClickListener() {
          @Override
          public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            BoardRow row = adapter.getItem(position);
            if (planner) selectMeal(row);
            else if (row.grocery != null && !row.grocery.planned) groceryEditor(row.grocery);
            else if (row.grocery != null) new MaterialAlertDialogBuilder(requireContext())
                .setMessage("Nguyên liệu này tự tính từ lịch ăn. Chỉnh món trong lịch để thay đổi số lượng.")
                .setPositiveButton("Đóng",null).show();
          }
        });
    list.setOnItemLongClickListener(
        new AdapterView.OnItemLongClickListener() {
          @Override
          public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
            onRowLongClick(adapter.getItem(position));
            return true;
          }
        });
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
      String[] types = {"Sáng", "Trưa", "Tối", "Ăn nhẹ"};
      for (int n = 0; n < 7; n++, d.add(Calendar.DAY_OF_MONTH, 1)) {
        for (String type : types) {
          for (Meal m : meals) {
            if (!m.day.equals(day(d)) || !m.type.equals(type)) continue;
            BoardRow row = new BoardRow();
            row.day = day(d);row.type=type;row.meal=m;
            Recipe r=recipe(m.recipeId);
            row.title=r==null?"Món đã xóa":r.title;
            row.meta=new SimpleDateFormat("EEE dd/MM",new Locale("vi")).format(d.getTime())
                +" · "+type+" · Nhấn để sửa";
            rows.add(row);
          }
          BoardRow add = new BoardRow();
          add.day=day(d);add.type=type;
          add.title="+ Thêm món";
          add.meta=new SimpleDateFormat("EEE dd/MM",new Locale("vi")).format(d.getTime())
              +" · "+type;
          rows.add(add);
        }
      }
      Ui.set(root, R.id.board_empty, "4 bữa/ngày · nhiều món/bữa · giỏ tự đồng bộ");
    } else {
      for (Grocery g : groceries) {
        BoardRow row = new BoardRow();
        row.grocery = g;
        row.title = g.name;
        row.meta =
            (g.planned ? "✦ Tự đồng bộ · " : "Tự thêm · ") + g.category
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

  private void selectMeal(BoardRow row) {
    if (recipes.isEmpty()) {
      android.widget.Toast.makeText(
              requireContext(), "Hãy lưu công thức trước", android.widget.Toast.LENGTH_SHORT)
          .show();
      return;
    }
    View content =
        LayoutInflater.from(requireContext()).inflate(R.layout.dialog_recipe_picker, null);
    EditText search = content.findViewById(R.id.meal_search);
    ListView list = content.findViewById(R.id.meal_recipe_list);
    java.util.List<Recipe> matching = new ArrayList<>();
    ArrayAdapter<String> adapter =
        new ArrayAdapter<>(
            requireContext(), android.R.layout.simple_list_item_1, new ArrayList<>());
    list.setAdapter(adapter);
    Runnable filter =
        () -> {
          String query = Units.key(search.getText().toString());
          matching.clear();
          adapter.setNotifyOnChange(false);
          adapter.clear();
          adapter.add("Bỏ món khỏi bữa này");
          for (Recipe r : recipes)
            if (Units.key(r.title + " " + r.tags).contains(query)) {
              matching.add(r);
              adapter.add(r.title);
            }
          adapter.notifyDataSetChanged();
        };
    filter.run();
    search.addTextChangedListener(
        new android.text.TextWatcher() {
          public void beforeTextChanged(CharSequence s, int a, int c, int d) {}

          public void onTextChanged(CharSequence s, int a, int b, int c) {
            filter.run();
          }

          public void afterTextChanged(android.text.Editable e) {}
        });
    androidx.appcompat.app.AlertDialog dialog =
        new MaterialAlertDialogBuilder(requireContext())
            .setTitle(row.day + " · " + row.type)
            .setView(content)
            .setNegativeButton("Đóng", null)
            .create();
    list.setOnItemClickListener(
        (parent, view, position, id) -> {
          if (position == 0) {
            if (row.meal != null) vm.repository.run(() -> vm.repository.dao.deleteMeal(row.meal));
          } else {
            Meal meal = new Meal();
            meal.day=row.day; meal.type=row.type;
            meal.slot=row.day+"|"+row.type;
            meal.recipeId=matching.get(position-1).id;
            boolean primaryExists=false;
            for(Meal m:meals)
              if(m.day.equals(meal.day) && m.type.equals(meal.type) && m.extraId==0)
                primaryExists=true;
            final boolean hasPrimary=primaryExists;
            vm.repository.run(() -> {
              if(row.meal!=null){
                if(row.meal.extraId==0)vm.repository.dao.putMeal(meal);
                else{
                  vm.repository.dao.deleteMeal(row.meal);
                  vm.repository.dao.addMeal(meal);
                }
              }else if(hasPrimary) vm.repository.dao.addMeal(meal);
              else vm.repository.dao.putMeal(meal);
            });
          }
          dialog.dismiss();
        });
    dialog.show();
  }

  private void planGroceries() {
    // Derived groceries are automatically synchronized with every planner change.
    ((com.google.android.material.bottomnavigation.BottomNavigationView)
       requireActivity().findViewById(R.id.bottom)).setSelectedItemId(R.id.nav_grocery);
  }

  private void groceryEditor(Grocery original) {
    if(original!=null && original.planned) return;
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
    StringBuilder b = new StringBuilder("reCime · Danh sách đi chợ\n");
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

  private void onRowLongClick(BoardRow r) {
    if (planner) {
      if (r.meal != null)
        new MaterialAlertDialogBuilder(requireContext())
            .setMessage("Bỏ món khỏi bữa này?")
            .setNegativeButton("Hủy", null)
            .setPositiveButton(
                "Bỏ món", (d, w) -> vm.repository.run(() -> vm.repository.dao.deleteMeal(r.meal)))
            .show();
    } else {
      if(r.grocery==null || r.grocery.planned) return;
      new MaterialAlertDialogBuilder(requireContext())
          .setMessage("Xóa " + r.grocery.name + "?")
          .setNegativeButton("Hủy", null)
          .setPositiveButton(
              "Xóa", (d, w) -> vm.repository.run(() -> vm.repository.dao.deleteGrocery(r.grocery)))
          .show();
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
