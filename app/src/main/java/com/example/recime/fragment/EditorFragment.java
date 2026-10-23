package com.example.recime.fragment;

import com.example.recime.helper.Motion;

import android.os.Bundle;
import android.net.Uri;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import android.view.*;
import android.widget.*;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.example.recime.*;
import com.example.recime.helper.RecipeParser;
import com.example.recime.helper.Ui;
import com.example.recime.helper.Units;
import com.example.recime.model.ParsedRecipe;
import com.example.recime.model.Recipe;
import com.example.recime.viewmodel.LabViewModel;
import com.example.recime.viewmodel.EditorSaveState;

public class EditorFragment extends Fragment {
  private Recipe recipe = new Recipe();
  private EditorSaveState photoState;
  private View activityRoot, dock, host;
  private android.graphics.drawable.Drawable previousBackground;
  private int previousDockVisibility, previousHostMargin;
  private boolean previousLightStatus, previousLightNavigation;
  private final ActivityResultLauncher<PickVisualMediaRequest> photoPicker =
      registerForActivityResult(new ActivityResultContracts.PickMultipleVisualMedia(5),
          uris -> photoState.importPhotos(uris, -1));
  private final ActivityResultLauncher<PickVisualMediaRequest> replacementPicker =
      registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
        int index = photoState.replacementIndex;
        photoState.replacementIndex = -1;
        if (uri != null) photoState.importPhotos(java.util.Collections.singletonList(uri), index);
      });

  public EditorFragment() {
    super(R.layout.fragment_editor);
  }

  public static EditorFragment create(long id, String raw, String source, String image) {
    EditorFragment f = new EditorFragment();
    Bundle b = new Bundle();
    b.putLong("id", id);
    b.putString("raw", raw);
    b.putString("source", source);
    b.putString("image", image);
    f.setArguments(b);
    return f;
  }

  @Override
  public void onViewCreated(View v, Bundle state) {
    enterEditorChrome();
    Motion.enter(v);
    LabViewModel vm = new ViewModelProvider(requireActivity()).get(LabViewModel.class);
    EditorSaveState saveState = new ViewModelProvider(this).get(EditorSaveState.class);
    photoState = saveState;
    if (state != null && state.containsKey("editorPhotos") && !saveState.photosInitialized) {
      saveState.photos.addAll(state.getStringArrayList("editorPhotos"));
      saveState.photosInitialized = true;
      saveState.replacementIndex = state.getInt("replacementPhoto", -1);
      saveState.restoreStaged(state.getStringArrayList("stagedPhotos"));
    }
    String[] sectionNames = {"info", "nutrition", "tags"};
    int[] toggles = {R.id.info_toggle, R.id.nutrition_toggle, R.id.tags_toggle};
    int[] panels = {R.id.info_panel, R.id.nutrition_panel, R.id.tags_panel};
    for (int index = 0; index < sectionNames.length; index++) {
      String section = sectionNames[index];
      int toggle = toggles[index], panel = panels[index];
      v.findViewById(toggle).setOnClickListener(x -> {
        View fields = v.findViewById(panel);
        fields.setVisibility(fields.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
      });
      if (state != null && state.getBoolean("panel_" + section)) v.findViewById(panel).setVisibility(View.VISIBLE);
    }
    ((EditText) v.findViewById(R.id.edit_servings)).addTextChangedListener(new android.text.TextWatcher() {
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
      public void onTextChanged(CharSequence s, int start, int before, int count) {
        Ui.set(v, R.id.info_toggle, "Info · " + s + " serves  ›");
      }
      public void afterTextChanged(android.text.Editable text) {}
    });
    boolean[] loaded = {false};
    v.findViewById(R.id.back).setOnClickListener(x -> getParentFragmentManager().popBackStack());
    Bundle args = requireArguments();
    long id = args.getLong("id");
    if (id > 0)
      vm.recipes.observe(
          getViewLifecycleOwner(),
          list -> {
            if (!loaded[0])
              for (Recipe r : list)
                if (r.id == id) {
                  recipe = new Recipe(r);
                  initializePhotos(recipe.photoPaths());
                  loaded[0] = true;
                  if (state == null) fill(v);
                  break;
                }
            v.findViewById(R.id.save).setEnabled(!saveState.saving && !saveState.importingPhotos && loaded[0]);
          });
    else {
      recipe.image = args.getString("image", "");
      recipe.source = args.getString("source", "");
      initializePhotos(recipe.photoPaths());
      String raw = args.getString("raw");
      if (raw != null) {
        ParsedRecipe p = RecipeParser.parse(raw);
        recipe.title = p.title;
        recipe.ingredients = p.ingredients;
        recipe.steps = p.steps;
          recipe.servingsKnown = p.servings != null;
          if (p.servings != null) recipe.servings = p.servings;
          recipe.attribution = p.attribution;
      }
      if (state == null) fill(v);
    }
    v.findViewById(R.id.save).setEnabled(!saveState.saving && !saveState.importingPhotos && (id == 0 || loaded[0]));
    v.findViewById(R.id.add_editor_photos).setOnClickListener(x -> {
      if (!saveState.photosInitialized || saveState.saving || saveState.importingPhotos || saveState.photos.size() >= 5) return;
      try {
        int remaining = 5 - saveState.photos.size();
        if (remaining == 1) {
          saveState.replacementIndex = -1;
          replacementPicker.launch(imageRequest());
        } else photoPicker.launch(new PickVisualMediaRequest.Builder()
            .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
            .setMaxItems(remaining).build());
      }
      catch (Exception error) { Ui.set(v, R.id.edit_error, "Không mở được Photo Picker: " + error.getMessage()); }
    });
    saveState.photoRevision.observe(getViewLifecycleOwner(), revision -> {
      renderPhotos(v);
      v.findViewById(R.id.save).setEnabled(!saveState.saving && !saveState.importingPhotos && (id == 0 || loaded[0]));
    });
    saveState.photoError.observe(getViewLifecycleOwner(), error -> {
      if (error != null) {
        Ui.set(v, R.id.edit_error, error);
        saveState.photoError.setValue(null);
      }
    });
    saveState.outcome.observe(getViewLifecycleOwner(), error -> {
      if (error == null) return;
      saveState.saving = false;
      renderPhotos(v);
      v.findViewById(R.id.save).setEnabled(true);
      saveState.outcome.setValue(null);
      if (error.isEmpty()) {
        vm.repository.message.setValue("Đã lưu công thức");
        getParentFragmentManager().popBackStack();
      } else Ui.set(v, R.id.edit_error, "Không lưu được: " + error);
    });
    v.findViewById(R.id.estimate_nutrition).setOnClickListener(x->{
      try{
        String ingredients=Ui.text(v,R.id.edit_ingredients);
        double servings=Double.parseDouble(Ui.text(v,R.id.edit_servings).replace(',','.'));
        if(ingredients.isEmpty() || !Double.isFinite(servings) || servings<=0)
          throw new IllegalArgumentException("Nhập nguyên liệu và khẩu phần hợp lệ.");
        android.content.Context appContext=requireContext().getApplicationContext();
        Ui.set(v,R.id.edit_error,"Đang đối chiếu USDA FoodData Central…");
        vm.repository.extraction.execute(()->{
          try{
            org.json.JSONObject result=
              new com.example.recime.AIservice.LocalAiClient(appContext)
                .estimateNutrition(ingredients,servings);
            v.post(()->{
              if(getView()!=v)return;
              if(!result.optBoolean("complete")){
                StringBuilder msg=new StringBuilder("Thiếu dữ liệu cho các nguyên liệu:\n");
                org.json.JSONArray missing=result.optJSONArray("missing");
                if(missing!=null)for(int n=0;n<missing.length();n++){
                  org.json.JSONObject item=missing.optJSONObject(n);
                  if(item!=null)msg.append("• ").append(item.optString("input"))
                    .append(": ").append(item.optString("reason")).append("\n");
                }
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(v.getContext())
                  .setTitle("Không thể ước tính đầy đủ").setMessage(msg.toString())
                  .setPositiveButton("Đóng",null).show();
                Ui.set(v,R.id.edit_error,"Thiếu dữ liệu. Không tự điền số dinh dưỡng.");
                return;
              }
              org.json.JSONObject per=result.optJSONObject("perServing");
              if(per==null)return;
              org.json.JSONArray matched=result.optJSONArray("matched");
              StringBuilder sources=new StringBuilder("USDA FoodData Central (ước tính): ");
              if(matched!=null)for(int n=0;n<matched.length();n++){
                org.json.JSONObject item=matched.optJSONObject(n);
                if(item!=null)sources.append(item.optString("description"))
                  .append(" [").append(item.optString("fdcId")).append("]; ");
              }
              String warning="Chỉ áp dụng nếu tên thực phẩm USDA khớp nguyên liệu bạn dùng. "+
                "Giá trị là ước tính, không đảm bảo an toàn thực phẩm hay dị ứng.\n\n"+
                sources+"\n\nPer serving: "+per.optDouble("calories")+" kcal, "+
                per.optDouble("protein")+" g protein, "+
                per.optDouble("carbs")+" g carb, "+
                per.optDouble("fat")+" g fat.";
              new com.google.android.material.dialog.MaterialAlertDialogBuilder(v.getContext())
                .setTitle("Kiểm tra mapping USDA trước khi áp dụng")
                .setMessage(warning).setNegativeButton("Hủy",null)
                .setPositiveButton("Tôi đã kiểm tra, áp dụng",(d,w)->{
                  Ui.set(v,R.id.edit_kcal,per.optDouble("calories"));
                  Ui.set(v,R.id.edit_protein,per.optDouble("protein"));
                  Ui.set(v,R.id.edit_carbs,per.optDouble("carbs"));
                  Ui.set(v,R.id.edit_fat,per.optDouble("fat"));
                  Ui.set(v,R.id.edit_nutrition_source,sources.toString());
                  Ui.set(v,R.id.edit_error,"Đã điền số ước tính USDA sau xác nhận.");
                }).show();
            });
          }catch(Exception e){
            v.post(()->{
              if(getView()==v)Ui.set(v,R.id.edit_error,"USDA không khả dụng: "+e.getMessage());
            });
          }
        });
      }catch(Exception e){Ui.set(v,R.id.edit_error,e.getMessage());}
    });
    v.findViewById(R.id.save)
        .setOnClickListener(
            x -> {
              if (saveState.saving || saveState.importingPhotos || (id > 0 && !loaded[0])) return;
              try {
                Recipe candidate = new Recipe(recipe);
                candidate.setPhotoPaths(saveState.photos);
                String title = Ui.text(v, R.id.edit_title),
                    ing = Ui.text(v, R.id.edit_ingredients),
                    steps = Ui.text(v, R.id.edit_steps);
                if (title.isEmpty() || ing.isEmpty() || steps.isEmpty())
                  throw new IllegalArgumentException("Cần có tên món, nguyên liệu và cách làm.");
                double servings =
                    Double.parseDouble(Ui.text(v, R.id.edit_servings).replace(',', '.'));
                if (!Double.isFinite(servings) || servings <= 0 || servings > 10000)
                  throw new IllegalArgumentException("Khẩu phần phải từ lớn hơn 0 đến 10000.");
                candidate.title = title;
                candidate.ingredients = ing;
                candidate.steps = steps;
                candidate.servings = servings;
                candidate.cookbook = Ui.text(v, R.id.edit_book);
                if (candidate.id == 0 && candidate.cookbook.isEmpty()) candidate.cookbook = "Món nhà";
                candidate.tags = Ui.text(v, R.id.edit_tags);
                candidate.source = Ui.text(v, R.id.edit_source);
                candidate.calories = optional(v, R.id.edit_kcal);
                candidate.protein = optional(v, R.id.edit_protein);
                candidate.carbs = optional(v, R.id.edit_carbs);
                candidate.fat = optional(v, R.id.edit_fat);
                candidate.nutritionSource = Ui.text(v, R.id.edit_nutrition_source);
                if ((candidate.calories != null
                        || candidate.protein != null
                        || candidate.carbs != null
                        || candidate.fat != null)
                    && candidate.nutritionSource.isEmpty())
                  throw new IllegalArgumentException(
                      "Hãy ghi nguồn dinh dưỡng hoặc để trống các giá trị.");
                saveState.saving = true;
                renderPhotos(v);
                v.findViewById(R.id.save).setEnabled(false);
                final java.util.List<String> previousPhotos = recipe.photoPaths();
                final android.content.Context storage = requireContext().getApplicationContext();
                vm.repository.io.execute(
                    () -> {
                      try {
                        if (candidate.id == 0) candidate.id = vm.repository.dao.insert(candidate);
                        else vm.repository.dao.update(candidate);
                        saveState.photosSaved(candidate.photoPaths());
                        if (!previousPhotos.isEmpty()) {
                          try {
                            com.example.recime.helper.RecipePhotoStore.pruneRemoved(
                                storage, previousPhotos, vm.repository.dao.recipeSnapshot());
                          } catch (RuntimeException cleanupFailure) {
                            // The recipe is committed; a failed cleanup keeps files for safety.
                          }
                        }
                        saveState.outcome.postValue("");
                      } catch (Exception e) {
                        saveState.photoSaveFailed();
                        saveState.outcome.postValue(e.getMessage() == null
                            ? e.getClass().getSimpleName() : e.getMessage());
                      }
                    });
              } catch (Exception e) {
                Ui.set(
                    v,
                    R.id.edit_error,
                    e instanceof NumberFormatException
                        ? "Định lượng/dinh dưỡng cần là số hợp lệ."
                        : e.getMessage());
              }
            });
  }

  private PickVisualMediaRequest imageRequest() {
    return new PickVisualMediaRequest.Builder()
        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE).build();
  }

  private void initializePhotos(java.util.List<String> paths) {
    if (!photoState.photosInitialized) {
      photoState.photos.addAll(paths);
      photoState.photosInitialized = true;
      photoState.changed();
    }
  }

  private void renderPhotos(View root) {
    LinearLayout strip = root.findViewById(R.id.editor_photos);
    strip.removeAllViews();
    boolean busy = photoState.saving || photoState.importingPhotos || !photoState.photosInitialized;
    Ui.set(root, R.id.photo_count, !photoState.photosInitialized ? "Đang tải ảnh…"
        : photoState.importingPhotos ? "Đang lưu bản sao ảnh…"
        : photoState.photos.size() + " / 5");
    root.findViewById(R.id.add_editor_photos).setEnabled(!busy && photoState.photos.size() < 5);
    for (int index = 0; index < photoState.photos.size(); index++) {
      final int position = index;
      final String path = photoState.photos.get(index);
      android.content.Context context = root.getContext();
      LinearLayout card = new LinearLayout(context);
      card.setOrientation(LinearLayout.VERTICAL);
      LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(dp(132), -2);
      layout.setMarginEnd(dp(12));
      strip.addView(card, layout);
      ImageView image = new ImageView(context);
      image.setScaleType(ImageView.ScaleType.CENTER_CROP);
      image.setContentDescription("Xem ảnh " + (index + 1));
      card.addView(image, new LinearLayout.LayoutParams(-1, dp(132)));
      Ui.image(image, path);
      image.setOnClickListener(x -> {
        ImageView preview = new ImageView(context);
        preview.setAdjustViewBounds(true);
        preview.setMinimumHeight(dp(180));
        preview.setMaxHeight(Math.round(getResources().getDisplayMetrics().heightPixels * 0.6f));
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setContentDescription("Ảnh " + (position + 1));
        Ui.image(preview, path);
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
            .setTitle("Ảnh " + (position + 1)).setView(preview).setPositiveButton("Đóng", null).show();
      });
      LinearLayout actions = new LinearLayout(context);
      card.addView(actions);
      for (boolean replace : new boolean[]{true, false}) {
        android.widget.Button button = new android.widget.Button(context);
        button.setText(replace ? "Thay" : "Xóa");
        button.setTextSize(12);
        button.setPadding(0, 0, 0, 0);
        button.setContentDescription((replace ? "Thay ảnh " : "Xóa ảnh ") + (index + 1));
        button.setEnabled(!busy);
        actions.addView(button, new LinearLayout.LayoutParams(0, dp(48), 1));
        button.setOnClickListener(x -> {
          if (!replace) photoState.removePhoto(position);
          else {
            photoState.replacementIndex = position;
            try { replacementPicker.launch(imageRequest()); }
            catch (Exception error) { Ui.set(root, R.id.edit_error, "Không mở được Photo Picker"); }
          }
        });
      }
    }
  }

  private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

  private void enterEditorChrome() {
    activityRoot = requireActivity().findViewById(R.id.main);
    dock = requireActivity().findViewById(R.id.bottom);
    host = requireActivity().findViewById(R.id.host);
    previousBackground = activityRoot.getBackground();
    previousDockVisibility = dock.getVisibility();
    previousHostMargin = ((ViewGroup.MarginLayoutParams) host.getLayoutParams()).bottomMargin;
    activityRoot.setBackgroundColor(android.graphics.Color.rgb(250, 248, 242));
    dock.setVisibility(View.GONE);
    ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) host.getLayoutParams();
    params.bottomMargin = 0; host.setLayoutParams(params);
    androidx.core.view.WindowInsetsControllerCompat bars = androidx.core.view.WindowCompat
        .getInsetsController(requireActivity().getWindow(), activityRoot);
    previousLightStatus = bars.isAppearanceLightStatusBars();
    previousLightNavigation = bars.isAppearanceLightNavigationBars();
    bars.setAppearanceLightStatusBars(true); bars.setAppearanceLightNavigationBars(true);
  }

  @Override public void onDestroyView() {
    if (activityRoot != null) {
      activityRoot.setBackground(previousBackground);
      dock.setVisibility(previousDockVisibility);
      ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) host.getLayoutParams();
      params.bottomMargin = previousHostMargin; host.setLayoutParams(params);
      androidx.core.view.WindowInsetsControllerCompat bars = androidx.core.view.WindowCompat
          .getInsetsController(requireActivity().getWindow(), activityRoot);
      bars.setAppearanceLightStatusBars(previousLightStatus);
      bars.setAppearanceLightNavigationBars(previousLightNavigation);
      activityRoot = null; dock = null; host = null; previousBackground = null;
    }
    super.onDestroyView();
  }

  @Override public void onSaveInstanceState(Bundle state) {
    super.onSaveInstanceState(state);
    if (photoState != null && photoState.photosInitialized) {
      state.putStringArrayList("editorPhotos", new java.util.ArrayList<>(photoState.photos));
      state.putStringArrayList("stagedPhotos", photoState.stagedPaths());
      state.putInt("replacementPhoto", photoState.replacementIndex);
    }
    if (getView() != null) {
      state.putBoolean("panel_info", getView().findViewById(R.id.info_panel).getVisibility() == View.VISIBLE);
      state.putBoolean("panel_nutrition", getView().findViewById(R.id.nutrition_panel).getVisibility() == View.VISIBLE);
      state.putBoolean("panel_tags", getView().findViewById(R.id.tags_panel).getVisibility() == View.VISIBLE);
    }
  }

  private Double optional(View v, int id) {
    String s = Ui.text(v, id);
    if (s.isEmpty()) return null;
    double d = Double.parseDouble(s.replace(',', '.'));
    if (!Double.isFinite(d) || d < 0)
      throw new IllegalArgumentException("Dinh dưỡng phải là số không âm.");
    return d;
  }

  private void fill(View v) {
    Ui.set(v, R.id.edit_title, recipe.title);
    Ui.set(v, R.id.edit_book, recipe.cookbook);
    Ui.set(v, R.id.edit_tags, recipe.tags);
    Ui.set(v, R.id.edit_servings, Units.format(recipe.servings));
    Ui.set(v, R.id.edit_ingredients, recipe.ingredients);
    Ui.set(v, R.id.edit_steps, recipe.steps);
    Ui.set(v, R.id.edit_source, recipe.source);
    Ui.set(v, R.id.edit_kcal, recipe.calories);
    Ui.set(v, R.id.edit_protein, recipe.protein);
    Ui.set(v, R.id.edit_carbs, recipe.carbs);
    Ui.set(v, R.id.edit_fat, recipe.fat);
    Ui.set(v, R.id.edit_nutrition_source, recipe.nutritionSource);
  }
}
