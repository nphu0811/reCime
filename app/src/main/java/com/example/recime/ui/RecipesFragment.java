package com.example.recime.ui;

import android.os.Bundle;
import android.text.*;
import android.view.*;
import android.widget.*;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.*;
import com.example.recime.*;
import com.example.recime.data.Recipe;
import com.example.recime.domain.Units;
import java.util.*;

public class RecipesFragment extends Fragment {
  private List<Recipe> all = new ArrayList<>(), shown = new ArrayList<>();
  private View root;
  private RecipeAdapter adapter;
  private String book = "Tất cả";
  private LabViewModel vm;

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
    vm = new ViewModelProvider(requireActivity()).get(LabViewModel.class);
    if (getArguments() != null && getArguments().getBoolean("discover")) {
      Ui.set(v, R.id.heading, "Khám phá từ căn bếp");
      Ui.set(v, R.id.eyebrow, "Ý TƯỞNG OFFLINE · NGUYÊN LIỆU CÓ SẴN");
    }
    RecyclerView list = v.findViewById(R.id.list);
    list.setLayoutManager(new GridLayoutManager(requireContext(), 2));
    adapter = new RecipeAdapter();
    list.setAdapter(adapter);
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
    shown = new ArrayList<>();
    for (Recipe r : all) {
      String hay = Units.key(r.title + " " + r.tags + " " + r.ingredients);
      boolean ok = true;
      for (String token : q.split("[,\\s]+")) if (!hay.contains(token)) ok = false;
      if (ok && (!favorite || r.favorite) && (book.equals("Tất cả") || book.equals(r.cookbook)))
        shown.add(r);
    }
    Ui.set(
        root,
        R.id.empty,
        shown.isEmpty()
            ? (Boolean.TRUE.equals(vm.repository.ready.getValue())
                ? "Chưa có công thức phù hợp. Thêm món đầu tiên của bạn."
                : "Đang tải…")
            : shown.size() + " công thức · lưu trên thiết bị");
    adapter.notifyDataSetChanged();
  }

  class RecipeAdapter extends RecyclerView.Adapter<RecipeHolder> {
    public RecipeHolder onCreateViewHolder(ViewGroup p, int t) {
      return new RecipeHolder(
          LayoutInflater.from(p.getContext()).inflate(R.layout.item_recipe, p, false));
    }

    public int getItemCount() {
      return shown.size();
    }

    public void onBindViewHolder(RecipeHolder h, int pos) {
      Recipe r = shown.get(pos);
      Ui.set(h.itemView, R.id.title, r.title);
      Ui.set(
          h.itemView,
          R.id.meta,
          (r.favorite ? "♥ · " : "") + r.cookbook + " · " + Units.format(r.servings) + " phần");
      Ui.image(h.itemView.findViewById(R.id.photo), r.image);
      h.itemView.setOnClickListener(
          v -> ((MainActivity) requireActivity()).open(DetailFragment.create(r.id)));
    }
  }

  static class RecipeHolder extends RecyclerView.ViewHolder {
    RecipeHolder(View v) {
      super(v);
    }
  }

  @Override
  public void onDestroyView() {
    root = null;
    super.onDestroyView();
  }
}
