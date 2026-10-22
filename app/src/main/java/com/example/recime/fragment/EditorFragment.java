package com.example.recime.fragment;

import com.example.recime.helper.Motion;

import android.os.Bundle;
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

public class EditorFragment extends Fragment {
  private Recipe recipe = new Recipe();
  private boolean saving;

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
    Motion.enter(v);
    LabViewModel vm = new ViewModelProvider(requireActivity()).get(LabViewModel.class);
    v.findViewById(R.id.back).setOnClickListener(x -> getParentFragmentManager().popBackStack());
    Bundle args = requireArguments();
    long id = args.getLong("id");
    if (id > 0)
      vm.recipes.observe(
          getViewLifecycleOwner(),
          list -> {
            if (!saving)
              for (Recipe r : list)
                if (r.id == id) {
                  recipe = r;
                  if (state == null) fill(v);
                  break;
                }
          });
    else {
      recipe.image = args.getString("image", "");
      recipe.source = args.getString("source", "");
      String raw = args.getString("raw");
      if (raw != null) {
        ParsedRecipe p = RecipeParser.parse(raw);
        recipe.title = p.title;
        recipe.ingredients = p.ingredients;
        recipe.steps = p.steps;
      }
      if (state == null) fill(v);
    }
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
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
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
              new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
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
              if (saving) return;
              try {
                String title = Ui.text(v, R.id.edit_title),
                    ing = Ui.text(v, R.id.edit_ingredients),
                    steps = Ui.text(v, R.id.edit_steps);
                if (title.isEmpty() || ing.isEmpty() || steps.isEmpty())
                  throw new IllegalArgumentException("Cần có tên món, nguyên liệu và cách làm.");
                double servings =
                    Double.parseDouble(Ui.text(v, R.id.edit_servings).replace(',', '.'));
                if (!Double.isFinite(servings) || servings <= 0 || servings > 10000)
                  throw new IllegalArgumentException("Khẩu phần phải từ lớn hơn 0 đến 10000.");
                recipe.title = title;
                recipe.ingredients = ing;
                recipe.steps = steps;
                recipe.servings = servings;
                recipe.cookbook = Ui.text(v, R.id.edit_book);
                if (recipe.id == 0 && recipe.cookbook.isEmpty()) recipe.cookbook = "Món nhà";
                recipe.tags = Ui.text(v, R.id.edit_tags);
                recipe.source = Ui.text(v, R.id.edit_source);
                recipe.calories = optional(v, R.id.edit_kcal);
                recipe.protein = optional(v, R.id.edit_protein);
                recipe.carbs = optional(v, R.id.edit_carbs);
                recipe.fat = optional(v, R.id.edit_fat);
                recipe.nutritionSource = Ui.text(v, R.id.edit_nutrition_source);
                if ((recipe.calories != null
                        || recipe.protein != null
                        || recipe.carbs != null
                        || recipe.fat != null)
                    && recipe.nutritionSource.isEmpty())
                  throw new IllegalArgumentException(
                      "Hãy ghi nguồn dinh dưỡng hoặc để trống các giá trị.");
                saving = true;
                v.findViewById(R.id.save).setEnabled(false);
                vm.repository.io.execute(
                    () -> {
                      try {
                        if (recipe.id == 0) recipe.id = vm.repository.dao.insert(recipe);
                        else vm.repository.dao.update(recipe);
                        v.post(
                            () -> {
                              if (isAdded()) {
                                vm.repository.message.setValue("Đã lưu công thức");
                                getParentFragmentManager().popBackStack();
                              }
                            });
                      } catch (Exception e) {
                        v.post(
                            () -> {
                              saving = false;
                              v.findViewById(R.id.save).setEnabled(true);
                              Ui.set(v, R.id.edit_error, "Không lưu được: " + e.getMessage());
                            });
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
