package com.example.recime.fragment;

import com.example.recime.helper.Motion;
import com.example.recime.helper.RecipeExport;
import com.example.recime.model.Meal;

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
  private double baseServings = -1;
  private final com.example.recime.helper.RecipeGallery gallery = new com.example.recime.helper.RecipeGallery();
  private com.example.recime.viewmodel.DetailActionState actions;
  private final int[] actionIds = {R.id.edit, R.id.delete, R.id.favorite, R.id.to_grocery,
      R.id.add_to_book, R.id.to_plan, R.id.cooking_mode, R.id.export_pdf, R.id.print_recipe,
      R.id.ask_recipe_ai, R.id.minus, R.id.plus, R.id.unit_settings, R.id.metric};

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
    Motion.enter(v);
    root = v;
    LabViewModel vm = new ViewModelProvider(requireActivity()).get(LabViewModel.class);
    actions = new ViewModelProvider(this).get(com.example.recime.viewmodel.DetailActionState.class);
    if (state != null) {
      portions = state.getDouble("portions", -1);
      baseServings = state.getDouble("base_servings", -1);
      gallery.restore(state.getInt("photo_index", 0), state.getString("photo_path", ""));
    }
    actions.outcome.observe(getViewLifecycleOwner(), result -> {
      if (result == null) return;
      actions.outcome.setValue(null);
      if (result.equals("deleted")) {
        getParentFragmentManager().popBackStack();
      } else {
        render();
        if (!result.equals("favorite")) {
          Ui.set(v, R.id.detail_status, result);
          v.findViewById(R.id.detail_status).setVisibility(View.VISIBLE);
        }
      }
    });
    v.findViewById(R.id.detail_photo_previous).setOnClickListener(x -> { gallery.move(-1); renderGallery(); });
    v.findViewById(R.id.detail_photo_next).setOnClickListener(x -> { gallery.move(1); renderGallery(); });
    vm.repository.ready.observe(getViewLifecycleOwner(), ready -> { if (recipe == null) render(); });
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
            if (portions < 0 || portions == baseServings) portions = recipe.servings;
            baseServings = recipe.servings;
            gallery.update(recipe);
          }
          render();
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
                final long id = recipe.id;
                final boolean favorite = !recipe.favorite;
                mutate(vm, () -> vm.repository.dao.setFavorite(id, favorite), "favorite");
              }
            });
    v.findViewById(R.id.delete)
        .setOnClickListener(
            x -> {
              if (recipe != null)
                new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Xóa công thức?")
                    .setMessage(
                        "Các bữa có món này cũng được xóa và nguyên liệu từ lịch được cập nhật. "
                            + "Nguyên liệu tự thêm vào giỏ được giữ lại.")
                    .setNegativeButton("Giữ lại", null)
                    .setPositiveButton(
                        "Xóa",
                        (d, w) -> {
                          if (recipe == null || actions.busy) return;
                          final Recipe selected = new Recipe(recipe);
                          final android.content.Context app = requireContext().getApplicationContext();
                          mutate(vm, () -> {
                            vm.repository.db.deleteRecipe(selected);
                            try {
                              com.example.recime.helper.RecipePhotoStore.pruneRemoved(app,
                                  selected.photoPaths(), vm.repository.dao.recipeSnapshot());
                            } catch (Exception ignored) { /* DB deletion has already committed. */ }
                          }, "deleted");
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
    v.findViewById(R.id.add_to_book).setOnClickListener(x->{
      if(recipe==null)return;
      long recipeId=recipe.id;
      vm.repository.io.execute(() -> {
        List<String> books = vm.repository.dao.bookNamesSnapshot();
        List<String> memberships = vm.repository.dao.cookbookNamesForRecipe(recipeId);
        v.post(() -> {
          if (getView() != v || !isAdded()) return;
          if (books.isEmpty()) {
            Toast.makeText(requireContext(), "Hãy tạo cookbook trong Home", Toast.LENGTH_SHORT).show();
            return;
          }
          boolean[] selected = new boolean[books.size()];
          for (int index = 0; index < books.size(); index++)
            selected[index] = memberships.contains(books.get(index));
          new MaterialAlertDialogBuilder(requireContext()).setTitle("Quản lý cookbooks")
            .setMultiChoiceItems(books.toArray(new String[0]), selected,
                (dialog, which, checked) -> selected[which] = checked)
            .setNegativeButton("Hủy", null)
            .setPositiveButton("Lưu", (dialog, which) -> {
              List<String> draft = new ArrayList<>();
              for (int index = 0; index < books.size(); index++)
                if (selected[index]) draft.add(books.get(index));
              vm.repository.run(() -> {
                vm.repository.dao.replaceRecipeCookbooks(recipeId, draft);
                vm.repository.message.postValue("Đã cập nhật cookbooks; công thức được giữ lại");
              });
            }).show();
        });
      });
    });
    v.findViewById(R.id.cooking_mode).setOnClickListener(x->{
      if(recipe!=null)((MainActivity)requireActivity()).open(CookingFragment.create(recipe.id));
    });
    v.findViewById(R.id.to_plan).setOnClickListener(x->{
      if(recipe!=null)pickPlanDate(vm);
    });
    actions.aiOutcome.observe(getViewLifecycleOwner(), output -> {
      if (output == null) return;
      actions.aiOutcome.setValue(null);
      render();
      new MaterialAlertDialogBuilder(requireContext()).setTitle("Trợ lý nấu ăn")
          .setMessage(output).setPositiveButton("Đóng", null).show();
    });
    v.findViewById(R.id.ask_recipe_ai).setOnClickListener(x -> {
      if (recipe == null || actions.aiBusy) return;
      final Recipe selected = new Recipe(recipe);
      EditText question = new EditText(requireContext());
      question.setHint("VD: Tôi có thể thay bơ bằng gì?");
      question.setSingleLine(false); question.setMinLines(2);
      androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
          .setTitle("Hỏi AI về " + selected.title).setView(question)
          .setNegativeButton("Hủy", null).setPositiveButton("Hỏi AI", null).create();
      dialog.setOnShowListener(d -> dialog.getButton(-1).setOnClickListener(button -> {
        String text = question.getText().toString().trim();
        if (text.isEmpty()) { question.setError("Nhập câu hỏi"); return; }
        final android.content.Context app = requireContext().getApplicationContext();
        final com.example.recime.viewmodel.DetailActionState target = actions;
        target.aiBusy = true; render(); dialog.dismiss();
        Toast.makeText(requireContext(), "Đang hỏi AI từ backend…", Toast.LENGTH_SHORT).show();
        vm.repository.extraction.execute(() -> {
          String answer;
          try {
            answer = new com.example.recime.AIservice.LocalAiClient(app)
                .ask(selected, text).optString("answer", "");
            if (answer.trim().isEmpty()) answer = "AI không có câu trả lời";
          } catch (Exception error) { answer = "Không thể hỏi AI: " + error.getMessage(); }
          target.aiBusy = false;
          target.aiOutcome.postValue(answer);
        });
      }));
      dialog.show();
    });
    v.findViewById(R.id.export_pdf).setOnClickListener(x->{
      if(recipe==null)return;
      try{RecipeExport.share(requireActivity(),recipe);}
      catch(Exception error){Toast.makeText(requireContext(),"Lỗi xuất PDF: "+error.getMessage(),Toast.LENGTH_LONG).show();}
    });
    v.findViewById(R.id.print_recipe).setOnClickListener(x->{
      if(recipe==null)return;
      try{RecipeExport.print(requireActivity(),recipe);}
      catch(Exception error){Toast.makeText(requireContext(),"Lỗi in: "+error.getMessage(),Toast.LENGTH_LONG).show();}
    });
  }

  private void pickPlanDate(LabViewModel vm){
    final long recipeId = recipe.id;
    final String title = recipe.title;
    final double selectedPortions = portions;
    Calendar date=Calendar.getInstance();
    new android.app.DatePickerDialog(requireContext(),(picker,year,month,day)->{
      Calendar dayValue=Calendar.getInstance();
      dayValue.set(year,month,day);
      String formatted=new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.ROOT)
          .format(dayValue.getTime());
      final String[] types={"Sáng","Trưa","Tối","Ăn nhẹ"};
      new MaterialAlertDialogBuilder(requireContext())
          .setTitle("Thêm "+title+" vào "+formatted)
          .setItems(types,(dialog,which)->{
            final long id=recipeId;
            final double servings=selectedPortions;
            final String type=types[which];
            vm.repository.run(()->{
              Meal next=new Meal();
              next.day=formatted;next.type=type;next.slot=formatted+"|"+type;
              next.recipeId=id;next.servings=servings;
              // The v4 table stores portions even for the first dish in a slot.
              // Legacy meals remain readable without a schema change.
              vm.repository.dao.addMeal(next);
              vm.repository.message.postValue("Đã thêm vào lịch · nguyên liệu tự đồng bộ");
            });
          }).show();
    },date.get(Calendar.YEAR),date.get(Calendar.MONTH),date.get(Calendar.DAY_OF_MONTH)).show();
  }

  private List<Ingredient> scaled() {
    List<Ingredient> result = new ArrayList<>();
    for (Ingredient i : RecipeParser.ingredients(recipe.ingredients))
      result.add(i.scale(portions / recipe.servings));
    return result;
  }

  private void render() {
    if (root == null) return;
    for (int id : actionIds) root.findViewById(id).setEnabled(recipe != null && !actions.busy);
    root.findViewById(R.id.detail_content).setVisibility(recipe == null ? View.GONE : View.VISIBLE);
    root.findViewById(R.id.detail_status).setVisibility(recipe == null || actions.busy ? View.VISIBLE : View.GONE);
    if (recipe == null) {
      boolean ready = Boolean.TRUE.equals(new ViewModelProvider(requireActivity())
          .get(LabViewModel.class).repository.ready.getValue());
      Ui.set(root, R.id.detail_status, ready ? "Công thức không còn tồn tại" : "Đang tải công thức…");
      return;
    }
    root.findViewById(R.id.ask_recipe_ai).setEnabled(!actions.busy && !actions.aiBusy);
    if (actions.busy) Ui.set(root, R.id.detail_status, "Đang lưu thay đổi…");
    Ui.set(root, R.id.detail_title, recipe.title);
    Ui.set(root, R.id.detail_meta, recipe.cookbook + " · " + recipe.tags);
    renderGallery();
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

  private void renderGallery() {
    if (root == null) return;
    boolean hasPhoto = gallery.count() > 0;
    root.findViewById(R.id.photo).setVisibility(hasPhoto ? View.VISIBLE : View.GONE);
    root.findViewById(R.id.detail_photo_empty).setVisibility(hasPhoto ? View.GONE : View.VISIBLE);
    root.findViewById(R.id.detail_gallery_controls).setVisibility(hasPhoto ? View.VISIBLE : View.GONE);
    ImageView photo = root.findViewById(R.id.photo);
    Ui.image(photo, gallery.path());
    photo.setContentDescription("Ảnh " + (gallery.index() + 1) + " / " + gallery.count());
    Ui.set(root, R.id.detail_photo_count, (gallery.index() + 1) + " / " + gallery.count());
    root.findViewById(R.id.detail_photo_previous).setEnabled(gallery.index() > 0);
    root.findViewById(R.id.detail_photo_next).setEnabled(gallery.index() + 1 < gallery.count());
  }

  private void mutate(LabViewModel vm, Runnable operation, String success) {
    if (actions.busy) return;
    actions.busy = true;
    render();
    final com.example.recime.viewmodel.DetailActionState target = actions;
    vm.repository.io.execute(() -> {
      String result = success;
      try { operation.run(); }
      catch (Exception error) { result = "Không thể lưu thay đổi: " + error.getMessage(); }
      target.busy = false;
      target.outcome.postValue(result);
    });
  }

  @Override public void onViewStateRestored(Bundle state) {
    super.onViewStateRestored(state);
    render(); // Metric checkbox restores after onViewCreated.
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
    b.putDouble("base_servings", baseServings);
    b.putInt("photo_index", gallery.index());
    b.putString("photo_path", gallery.path());
  }

  @Override
  public void onDestroyView() {
    root = null;
    super.onDestroyView();
  }
}
