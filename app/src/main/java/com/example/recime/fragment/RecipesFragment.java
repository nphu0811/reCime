package com.example.recime.fragment;

import android.os.Bundle;
import android.text.*;
import android.view.*;
import android.widget.*;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.example.recime.*;
import com.example.recime.activity.MainActivity;
import com.example.recime.adapter.RecipeAdapter;
import com.example.recime.helper.Motion;
import com.example.recime.helper.Ui;
import com.example.recime.helper.Units;
import com.example.recime.model.Recipe;
import com.example.recime.viewmodel.LabViewModel;
import java.util.*;

public class RecipesFragment extends Fragment {
  private List<Recipe> all = new ArrayList<>();
  private final List<Recipe> shown = new ArrayList<>();
  private View root;
  private RecipeAdapter adapter;
  private String book = "Tất cả";
  private LabViewModel vm;
  private String category = "all";

  public RecipesFragment() {
    super(R.layout.fragment_recipes);
  }

  public static RecipesFragment create(boolean discover) {
    RecipesFragment f = new RecipesFragment();
    Bundle b = new Bundle();
    b.putBoolean("discover", discover);
    f.setArguments(b);
    return f;
  }

  @Override
  public void onViewCreated(View v, Bundle state) {
    root = v;
    Motion.enter(v);
    vm = new ViewModelProvider(requireActivity()).get(LabViewModel.class);
    if (getArguments() != null && getArguments().getBoolean("discover")) {
      Ui.set(v, R.id.heading, "Khám phá từ căn bếp");
      Ui.set(v, R.id.eyebrow, "Ý TƯỞNG OFFLINE · NGUYÊN LIỆU CÓ SẴN");
    }
    v.findViewById(R.id.ai_camera)
        .setOnClickListener(
            x ->
                ((com.google.android.material.bottomnavigation.BottomNavigationView)
                        requireActivity().findViewById(R.id.bottom))
                    .setSelectedItemId(R.id.nav_import));
    ((com.google.android.material.chip.ChipGroup) v.findViewById(R.id.category_chips))
        .setOnCheckedStateChangeListener(
            (group, ids) -> {
              int selected = ids.isEmpty() ? R.id.chip_all : ids.get(0);
              category =
                  selected == R.id.chip_soup
                      ? "soup"
                      : selected == R.id.chip_main
                          ? "main"
                          : selected == R.id.chip_vegan
                              ? "vegan"
                              : selected == R.id.chip_sweet ? "sweet" : "all";
              filter();
            });
    GridView list = v.findViewById(R.id.list);
    adapter = new RecipeAdapter(requireContext(), R.layout.item_recipe, shown);
    list.setAdapter(adapter);
    list.setOnItemClickListener(
        new AdapterView.OnItemClickListener() {
          @Override
          public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            Recipe recipe = adapter.getItem(position);
            ((MainActivity) requireActivity()).open(DetailFragment.create(recipe.id));
          }
        });
    v.findViewById(R.id.add)
        .setOnClickListener(
            x -> ((MainActivity) requireActivity()).open(EditorFragment.create(0, null, "", "")));
    ((EditText) v.findViewById(R.id.search))
        .addTextChangedListener(
            new TextWatcher() {
              public void beforeTextChanged(CharSequence s, int a, int c, int d) {}

              public void onTextChanged(CharSequence s, int a, int b, int c) {
                filter();
              }

              public void afterTextChanged(Editable e) {}
            });
    ((CheckBox) v.findViewById(R.id.favorites)).setOnCheckedChangeListener((b, c) -> filter());
    ((Spinner) v.findViewById(R.id.books))
        .setOnItemSelectedListener(
            new AdapterView.OnItemSelectedListener() {
              public void onNothingSelected(AdapterView<?> p) {}

              public void onItemSelected(AdapterView<?> p, View x, int pos, long id) {
                book = p.getItemAtPosition(pos).toString();
                filter();
              }
            });
    vm.recipes.observe(
        getViewLifecycleOwner(),
        items -> {
          all = items;
          Recipe featured = null;
          for (Recipe r : all)
            if (!r.image.isEmpty()) {
              featured = r;
              if (r.title.equalsIgnoreCase("Bún bò Huế")) break;
            }
          if (featured != null) {
            final Recipe selected = featured;
            Ui.image(v.findViewById(R.id.hero_photo), selected.image);
            Ui.set(v, R.id.hero_title, selected.title);
            Ui.set(
                v,
                R.id.hero_meta,
                selected.catalogKey == null
                    ? "Món của bạn · Xem công thức →"
                    : "Wikibooks · Công thức mở →");
            v.findViewById(R.id.hero_card)
                .setOnClickListener(
                    x ->
                        ((MainActivity) requireActivity())
                            .open(DetailFragment.create(selected.id)));
          }
          LinkedHashSet<String> books = new LinkedHashSet<>();
          books.add("Tất cả");
          for (Recipe r : all) books.add(r.cookbook);
          List<String> values = new ArrayList<>(books);
          Spinner s = v.findViewById(R.id.books);
          String old = book;
          s.setAdapter(
              new ArrayAdapter<>(
                  requireContext(), android.R.layout.simple_spinner_dropdown_item, values));
          s.setSelection(Math.max(0, values.indexOf(old)));
          filter();
        });
    vm.repository.ready.observe(getViewLifecycleOwner(), ready -> filter());
  }

  private void filter() {
    if (root == null || adapter == null) return;
    String q = Units.key(Ui.text(root, R.id.search));
    boolean favorite = ((CheckBox) root.findViewById(R.id.favorites)).isChecked();
    shown.clear();
    for (Recipe r : all) {
      String hay = Units.key(r.title + " " + r.tags + " " + r.ingredients);
      boolean ok = true;
      String type = Units.key(r.title + " " + r.cookbook);
      boolean categoryOk =
          category.equals("all")
              || category.equals("soup") && type.matches(".*(bun|pho|canh|lau|hu tieu|mi quang).*")
              || category.equals("vegan") && type.contains("chay")
              || category.equals("sweet") && type.matches(".*(banh|che |xoi|kem).*")
              || category.equals("main") && !type.matches(".*(banh|che |kem|canh|bun|pho).*");
      for (String token : q.split("[,\\s]+")) if (!hay.contains(token)) ok = false;
      if (ok
          && categoryOk
          && (!favorite || r.favorite)
          && (book.equals("Tất cả") || book.equals(r.cookbook))) shown.add(r);
    }
    Ui.set(
        root,
        R.id.empty,
        shown.isEmpty()
            ? (Boolean.TRUE.equals(vm.repository.ready.getValue())
                ? "Chưa có công thức phù hợp. Thêm món đầu tiên của bạn."
                : "Đang tải…")
            : shown.size() + " công thức · sẵn sàng khi offline");
    adapter.notifyDataSetChanged();
  }

  @Override
  public void onDestroyView() {
    root = null;
    super.onDestroyView();
  }
}
