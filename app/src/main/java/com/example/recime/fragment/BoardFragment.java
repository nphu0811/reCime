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
  private int groceryOrder = 0;
  private boolean weekInitialized;
  private int firstDay;

  @Override public View onCreateView(LayoutInflater inflater,ViewGroup container,Bundle state){
    boolean plan=requireArguments().getBoolean("planner");
    android.content.Context context=plan?new ContextThemeWrapper(requireContext(),R.style.Theme_MealPlanner):requireContext();
    return LayoutInflater.from(context).inflate(plan?R.layout.fragment_planner:R.layout.fragment_board,container,false);
  }
  private android.content.Context boardContext(){return root==null?requireContext():root.getContext();}
  private android.content.SharedPreferences planPrefs(){return requireContext().getSharedPreferences("planner",0);}
  private void alignWeek(){week.setTimeInMillis(com.example.recime.helper.PlannerWeek.start(week,firstDay).getTimeInMillis());}


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
    firstDay=planPrefs().getInt("first_day",Calendar.MONDAY);
    if(firstDay<1||firstDay>7)firstDay=Calendar.MONDAY;
    if(state!=null)week.setTimeInMillis(state.getLong("week",week.getTimeInMillis()));
    else if(!weekInitialized)week.setTimeInMillis(System.currentTimeMillis());
    alignWeek();weekInitialized=true;
    if(planner)v.findViewById(R.id.plan_week_start).setOnClickListener(x->chooseWeekStart());
    Ui.set(v, R.id.board_title, planner ? "Meal Plan" : "Giỏ đi chợ");
    Ui.set(
        v,
        R.id.board_subtitle,
        planner
            ? "Lập lịch tuần · giữ tay nắm để chuyển món"
            : "Nguyên liệu theo lịch ăn tự cập nhật; món thêm tay giữ riêng.");
    v.findViewById(R.id.week_controls).setVisibility(planner ? View.VISIBLE : View.GONE);
    Ui.set(v, R.id.board_action, planner ? "Groceries ↗" : "+ Thêm nguyên liệu");
    Ui.set(v, R.id.board_secondary, planner ? "Hôm nay" : "Chia sẻ checklist");
    Spinner sort=v.findViewById(R.id.grocery_sort);
    sort.setVisibility(planner?View.GONE:View.VISIBLE);
    if(!planner){
      ArrayAdapter<String> sorting=new ArrayAdapter<>(requireContext(),
          android.R.layout.simple_spinner_dropdown_item,
          new String[]{"Theo nhóm","A → Z","Theo nguồn","Chưa mua trước"});
      sort.setAdapter(sorting);
      sort.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
        public void onNothingSelected(AdapterView<?> parent){}
        public void onItemSelected(AdapterView<?> parent,View item,int pos,long id){
          groceryOrder=pos;render();
        }
      });
    }
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
                alignWeek();
                render();
              } else share();
            });
    v.findViewById(R.id.board_secondary).setOnLongClickListener(x->{
      if(!planner)return false;
      Calendar end=(Calendar)week.clone();end.add(Calendar.DAY_OF_MONTH,6);
      String start=day(week),finish=day(end);
      new MaterialAlertDialogBuilder(boardContext()).setTitle("Xóa lịch ăn tuần này?")
        .setMessage("Giỏ tự đồng bộ lại, nguyên liệu nhập thủ công vẫn giữ.")
        .setNegativeButton("Hủy",null)
        .setPositiveButton("Xóa tuần",(d,w)->vm.repository.run(
            ()->vm.repository.dao.clearMealWeek(start,finish))).show();
      return true;
    });
    ListView list = v.findViewById(R.id.board_list);
    adapter =
        new BoardAdapter(
            list.getContext(),
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
    adapter.setDragListener(handle->{
      if(!(handle.getTag() instanceof Meal))return false;
      return handle.startDragAndDrop(android.content.ClipData.newPlainText("meal", ""),
          new View.DragShadowBuilder(handle),(Meal)handle.getTag(),0);
    });
    list.setAdapter(adapter);
    if(planner)list.setOnDragListener((target,event)->{
      if(!(event.getLocalState() instanceof Meal))return false;
      if(event.getAction()==DragEvent.ACTION_DRAG_LOCATION){
        int edge=(int)(48*target.getResources().getDisplayMetrics().density);
        if(event.getY()<edge)list.smoothScrollBy(-edge,100);
        else if(event.getY()>list.getHeight()-edge)list.smoothScrollBy(edge,100);
      }else if(event.getAction()==DragEvent.ACTION_DROP){
        int position=list.pointToPosition((int)event.getX(),(int)event.getY());
        if(position==AdapterView.INVALID_POSITION||position>=rows.size())return false;
        BoardRow destination=rows.get(position);Meal original=new Meal((Meal)event.getLocalState());
        String targetDay=destination.day;
        String targetType=destination.dayHeader?original.type:destination.type;
        vm.repository.run(()->vm.repository.dao.moveMeal(original,targetDay,targetType));
      }
      return true;
    });
    list.setOnItemClickListener(
        new AdapterView.OnItemClickListener() {
          @Override
          public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            BoardRow row = adapter.getItem(position);
            if (planner) { if(row.meal==null)selectMeal(row);else mealOptions(row); }
            else if (row.grocery != null && !row.grocery.planned) groceryEditor(row.grocery);
            else if (row.grocery != null) new MaterialAlertDialogBuilder(boardContext())
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
      String[] types = com.example.recime.helper.PlannerWeek.TYPES;
      for (int n = 0; n < 7; n++, d.add(Calendar.DAY_OF_MONTH, 1)) {
        BoardRow header=new BoardRow();header.dayHeader=true;header.day=day(d);header.type="";
        header.title=new SimpleDateFormat("EEEE · dd MMM",Locale.getDefault()).format(d.getTime());
        rows.add(header);
        for (String type : types) {
          for (Meal m : meals) {
            if (!m.day.equals(day(d)) || !m.type.equals(type)) continue;
            BoardRow row = new BoardRow();
            row.day = day(d);row.type=type;row.meal=m;
            Recipe r=recipe(m.recipeId);
            row.title=r==null?"Món đã xóa":r.title;
            double quantity=m.servings>0?m.servings:(r==null?0:r.servings);
            row.meta=com.example.recime.helper.PlannerWeek.label(type)+" · "+Units.format(quantity)
                +(r!=null&&!r.servingsKnown?" mẻ":" khẩu phần");
            rows.add(row);
          }
          BoardRow add = new BoardRow();
          add.day=day(d);add.type=type;
          add.title="+ Thêm món";
          add.meta=com.example.recime.helper.PlannerWeek.label(type);
          rows.add(add);
        }
      }
      long count=meals.stream().filter(m->m.day.compareTo(day(week))>=0&&m.day.compareTo(day(end))<=0).count();
      Ui.set(root,R.id.board_empty,count==0?"Chưa có món trong tuần · thêm món vào từng bữa":count+" món trong tuần · nhiều món mỗi bữa");
    } else {
      List<Grocery> sorted=new ArrayList<>(groceries);
      if(groceryOrder==1) sorted.sort((a,b)->a.name.compareToIgnoreCase(b.name));
      else if(groceryOrder==2) sorted.sort((a,b)->{
        if(a.planned!=b.planned)return a.planned?-1:1;
        return a.category.compareToIgnoreCase(b.category);
      });
      else if(groceryOrder==3) sorted.sort((a,b)->{
        if(a.checked!=b.checked)return a.checked?1:-1;
        return a.name.compareToIgnoreCase(b.name);
      });
      for (Grocery g : sorted) {
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
        LayoutInflater.from(boardContext()).inflate(R.layout.dialog_recipe_picker, null);
    EditText search = content.findViewById(R.id.meal_search);
    ListView list = content.findViewById(R.id.meal_recipe_list);
    java.util.List<Recipe> matching = new ArrayList<>();
    ArrayAdapter<String> adapter =
        new ArrayAdapter<>(
            boardContext(), android.R.layout.simple_list_item_1, new ArrayList<>());
    list.setAdapter(adapter);
    Runnable filter =
        () -> {
          String query = Units.key(search.getText().toString());
          matching.clear();
          adapter.setNotifyOnChange(false);
          adapter.clear();
          if(row.meal!=null)adapter.add("Bỏ món khỏi bữa này");
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
        new MaterialAlertDialogBuilder(boardContext())
            .setTitle(row.day + " · " + com.example.recime.helper.PlannerWeek.label(row.type))
            .setView(content)
            .setNegativeButton("Đóng", null)
            .create();
    list.setOnItemClickListener(
        (parent, view, position, id) -> {
          if (row.meal!=null && position == 0) {
            vm.repository.run(() -> vm.repository.dao.deleteMeal(row.meal));
          } else {
            Meal meal = new Meal();
            meal.day=row.day; meal.type=row.type;
            meal.slot=row.day+"|"+row.type;
            meal.recipeId=matching.get(position-(row.meal==null?0:1)).id;
            final Meal original=row.meal==null?null:new Meal(row.meal);
            vm.repository.run(() -> {
              if(original!=null)vm.repository.dao.editMeal(original,meal.day,meal.type,meal.recipeId,original.servings);
              else vm.repository.dao.addMeal(meal);
            });
          }
          dialog.dismiss();
        });
    dialog.show();
  }

  private void chooseWeekStart(){
    String[] labels={"Sunday","Monday","Tuesday","Wednesday","Thursday","Friday","Saturday"};
    new MaterialAlertDialogBuilder(boardContext()).setTitle("Ngày bắt đầu tuần")
        .setSingleChoiceItems(labels,firstDay-1,(dialog,index)->{
          firstDay=index+1;planPrefs().edit().putInt("first_day",firstDay).apply();
          alignWeek();render();dialog.dismiss();
          ((ListView)root.findViewById(R.id.board_list)).setSelection(0);
        }).setNegativeButton("Hủy",null).show();
  }

  private void mealOptions(BoardRow row){
    final Meal selected=new Meal(row.meal);
    new MaterialAlertDialogBuilder(boardContext()).setTitle(row.title)
        .setItems(new String[]{"Mở công thức","Đổi món","Khẩu phần","Chuyển món","Bỏ món"},(dialog,which)->{
          if(which==0)((com.example.recime.activity.MainActivity)requireActivity()).open(DetailFragment.create(selected.recipeId));
          else if(which==1)selectMeal(row);
          else if(which==2||which==3)PlannerItemDialog.create(selected,which==3).show(getParentFragmentManager(),"planner-item");
          else new MaterialAlertDialogBuilder(boardContext()).setMessage("Bỏ món khỏi bữa này?")
              .setNegativeButton("Hủy",null).setPositiveButton("Bỏ món",(d,w)->
                  vm.repository.run(()->vm.repository.dao.deleteMeal(selected))).show();
        }).show();
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
        new MaterialAlertDialogBuilder(boardContext())
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
      if(r.meal!=null)mealOptions(r);
    } else {
      if(r.grocery==null || r.grocery.planned) return;
      new MaterialAlertDialogBuilder(boardContext())
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
