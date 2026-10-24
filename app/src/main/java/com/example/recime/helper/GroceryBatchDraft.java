package com.example.recime.helper;
import com.example.recime.model.*;
import java.util.*;

/** Detached ingredient selections; collecting a batch never changes recipes or meals. */
public final class GroceryBatchDraft {
  public static final class Entry {
    public final String key,label;
    public final Recipe recipe;
    public final List<Ingredient> ingredients;
    public final boolean[] selected;
    public String servings;
    Entry(String key,String label,Recipe source,double portions){
      this.key=key;this.label=label;recipe=new Recipe(source);
      ingredients=RecipeParser.ingredients(recipe.ingredients);selected=new boolean[ingredients.size()];Arrays.fill(selected,true);
      if(!Double.isFinite(recipe.servings)||recipe.servings<=0)throw new IllegalArgumentException("Công thức thiếu định lượng gốc hợp lệ");
      servings=Double.toString(portions>0?portions:recipe.servings);
    }
    public double ratio(){
      double value=Double.parseDouble(servings.replace(',','.'));
      if(!Double.isFinite(value)||value<=0)throw new IllegalArgumentException("Khẩu phần phải lớn hơn 0");
      double ratio=value/recipe.servings;
      if(!Double.isFinite(ratio)||ratio<=0)throw new IllegalArgumentException("Khẩu phần không hợp lệ");
      return ratio;
    }
  }
  public final LinkedHashMap<String,Entry> entries=new LinkedHashMap<>();
  public void select(String key,String label,Recipe recipe,double portions){
    if(!entries.containsKey(key))entries.put(key,new Entry(key,label,recipe,portions));
  }
  public List<Ingredient> collect(){
    List<Ingredient> result=new ArrayList<>();
    for(Entry entry:entries.values()){
      double ratio=entry.ratio();
      for(int n=0;n<entry.ingredients.size();n++)if(entry.selected[n])result.add(entry.ingredients.get(n).scale(ratio));
    }
    if(result.isEmpty())throw new IllegalArgumentException("Chọn ít nhất một nguyên liệu");
    return result;
  }
}
