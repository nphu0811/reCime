package com.example.recime.fragment;

import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.example.recime.*;
import com.example.recime.activity.MainActivity;
import com.example.recime.helper.RecipeParser;
import com.example.recime.helper.Ui;
import com.example.recime.helper.Units;
import com.example.recime.model.Ingredient;
import com.example.recime.model.Recipe;
import com.example.recime.viewmodel.LabViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.*;

public class DetailFragment extends Fragment {
  private Recipe recipe;
  private double portions = -1;
  private View root;

  public DetailFragment() {
    super(R.layout.fragment_detail);
  }

  public static DetailFragment create(long id) {
    DetailFragment f = new DetailFragment();
    Bundle b = new Bundle();
    b.putLong("id", id);
    f.setArguments(b);
    return f;
  }

  @Override
  public void onViewCreated(View v, Bundle state) {
    root = v;
    LabViewModel vm = new ViewModelProvider(requireActivity()).get(LabViewModel.class);
    if (state != null) portions = state.getDouble("portions", -1);
    v.findViewById(R.id.back).setOnClickListener(x -> getParentFragmentManager().popBackStack());
    vm.recipes.observe(
        getViewLifecycleOwner(),
        list -> {
          recipe = null;
          for (Recipe r : list)
            if (r.id == requireArguments().getLong("id")) {
              recipe = r;
              break;
            }
          if (recipe != null) {
            if (portions < 0) portions = recipe.servings;
            render();
          }
        });
    v.findViewById(R.id.edit)
        .setOnClickListener(
            x -> {
              if (recipe != null)
                ((MainActivity) requireActivity())
                    .open(EditorFragment.create(recipe.id, null, "", ""));
            });
    v.findViewById(R.id.favorite)
        .setOnClickListener(
            x -> {
              if (recipe != null) {
                recipe.favorite = !recipe.favorite;
                vm.repository.save(recipe);
              }
            });
    v.findViewById(R.id.delete)
        .setOnClickListener(
            x -> {
              if (recipe != null)
                new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Xóa công thức?")
                    .setMessage(
                        "Các bữa có món này cũng được xóa. Danh sách đi chợ đã tạo được giữ lại.")
                    .setNegativeButton("Giữ lại", null)
                    .setPositiveButton(
                        "Xóa",
                        (d, w) -> {
                          vm.repository.delete(recipe);
                          getParentFragmentManager().popBackStack();
                        })
                    .show();
            });
    v.findViewById(R.id.minus)
        .setOnClickListener(
            x -> {
              if (recipe != null) {
                portions = Math.max(0.25, portions - 1);
                render();
              }
            });
    v.findViewById(R.id.plus)
        .setOnClickListener(
            x -> {
              if (recipe != null) {
                portions = Math.min(10000, portions + 1);
                render();
              }
            });
    ((CheckBox) v.findViewById(R.id.metric)).setOnCheckedChangeListener((b, c) -> render());
    v.findViewById(R.id.unit_settings).setOnClickListener(x -> settings());
    v.findViewById(R.id.to_grocery)
        .setOnClickListener(
            x -> {
              if (recipe != null)
                vm.repository.addIngredients(
                    scaled(),
                    Ui.tsp(requireContext()),
                    Ui.tbsp(requireContext()),
                    Ui.cup(requireContext()));
            });
  }

  private List<Ingredient> scaled() {
    List<Ingredient> result = new ArrayList<>();
    for (Ingredient i : RecipeParser.ingredients(recipe.ingredients))
      result.add(i.scale(portions / recipe.servings));
    return result;
  }

  private void render() {
    if (root == null || recipe == null) return;
    Ui.set(root, R.id.detail_title, recipe.title);
    Ui.set(root, R.id.detail_meta, recipe.cookbook + " · " + recipe.tags);
    Ui.image(root.findViewById(R.id.photo), recipe.image);
    Ui.set(root, R.id.favorite, recipe.favorite ? "♥ Đã thích" : "♡ Thích");
    Ui.set(
        root,
        R.id.portions,
        Units.format(portions) + (recipe.servingsKnown ? " khẩu phần" : " mẻ công thức"));
    StringBuilder ingredients = new StringBuilder();
    boolean metric = ((CheckBox) root.findViewById(R.id.metric)).isChecked();
    for (Ingredient i : scaled()) {
      String dim = Units.dimension(i.unit);
      if (metric && i.amount != null && (dim.equals("mass") || dim.equals("volume"))) {
        String target = dim.equals("mass") ? "g" : "ml";
        i =
            new Ingredient(
                i.name,
                Units.convert(
                    i.amount,
                    i.unit,
                    target,
                    Ui.tsp(requireContext()),
                    Ui.tbsp(requireContext()),
                    Ui.cup(requireContext())),
                target);
      }
      ingredients.append("• ").append(i.display()).append('\n');
    }
    Ui.set(root, R.id.detail_ingredients, ingredients.toString());
    StringBuilder steps = new StringBuilder();
    int n = 1;
    for (String s : recipe.steps.split("\n"))
      if (!s.trim().isEmpty()) steps.append(n++).append(". ").append(s).append("\n\n");
    Ui.set(root, R.id.detail_steps, steps.toString());
    String nutrition = "Dinh dưỡng · mỗi khẩu phần gốc\n";
    nutrition +=
        (recipe.calories == null
                ? "Calories: chưa đủ dữ liệu"
                : "Calories: " + Units.format(recipe.calories) + " kcal")
            + "\n";
    nutrition +=
        "Protein: "
            + macro(recipe.protein)
            + " · Carb: "
            + macro(recipe.carbs)
            + " · Fat: "
            + macro(recipe.fat);
    if (!recipe.nutritionSource.isEmpty())
      nutrition += "\nTự nhập; chưa xác minh. Nguồn: " + recipe.nutritionSource;
    Ui.set(root, R.id.nutrition, nutrition);
    Ui.set(
        root,
        R.id.source,
        "Nguồn công thức: "
            + (recipe.source.isEmpty() ? "Tự nhập" : recipe.source)
            + "\n\n"
            + recipe.attribution
            + (recipe.servingsKnown
                ? ""
                : "\n"
                    + "Nguồn chưa ghi số người ăn. Định lượng phía trên là toàn bộ công thức,"
                    + " tính theo mẻ.")
            + (recipe.imageAttribution.isEmpty()
                ? ""
                : "\n\nẢnh và giấy phép:\n" + recipe.imageAttribution));
  }

  private String macro(Double d) {
    return d == null ? "chưa đủ dữ liệu" : Units.format(d) + " g";
  }

  private void settings() {
    LinearLayout l = new LinearLayout(requireContext());
    l.setPadding(40, 10, 40, 10);
    l.setOrientation(LinearLayout.VERTICAL);
    EditText
        tsp = Ui.field(requireContext(), l, "1 tsp = ml", Units.format(Ui.tsp(requireContext()))),
        tbsp =
            Ui.field(requireContext(), l, "1 tbsp = ml", Units.format(Ui.tbsp(requireContext()))),
        cup = Ui.field(requireContext(), l, "1 cup = ml", Units.format(Ui.cup(requireContext())));
    for (EditText e : new EditText[] {tsp, tbsp, cup}) e.setInputType(8194);
    androidx.appcompat.app.AlertDialog dialog =
        new MaterialAlertDialogBuilder(requireContext())
            .setTitle("Chuẩn thể tích (ml)")
            .setMessage("Mặc định 5 / 15 / 240 ml. Lạng = 100 g. Không tự đổi g ↔ ml.")
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
                        float a = Float.parseFloat(tsp.getText().toString()),
                            b = Float.parseFloat(tbsp.getText().toString()),
                            c = Float.parseFloat(cup.getText().toString());
                        if (!Float.isFinite(a)
                            || !Float.isFinite(b)
                            || !Float.isFinite(c)
                            || a <= 0
                            || b <= 0
                            || c <= 0) throw new IllegalArgumentException();
                        Ui.prefs(requireContext())
                            .edit()
                            .putFloat("tsp", a)
                            .putFloat("tbsp", b)
                            .putFloat("cup", c)
                            .apply();
                        render();
                        dialog.dismiss();
                      } catch (Exception e) {
                        cup.setError("Nhập ba số dương hợp lệ");
                      }
                    }));
    dialog.show();
  }

  @Override
  public void onSaveInstanceState(Bundle b) {
    super.onSaveInstanceState(b);
    b.putDouble("portions", portions);
  }

  @Override
  public void onDestroyView() {
    root = null;
    super.onDestroyView();
  }
}
