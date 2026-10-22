package com.example.recime.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import com.example.recime.R;
import com.example.recime.helper.Ui;
import com.example.recime.helper.Units;
import com.example.recime.model.Recipe;
import java.util.List;

/** GridView adapter, cùng cách viết với project GridViewAdvanced. */
public class RecipeAdapter extends BaseAdapter {
  private final Context context;
  private final int layout;
  private final List<Recipe> recipeList;

  public RecipeAdapter(Context context, int layout, List<Recipe> recipeList) {
    this.context = context;
    this.layout = layout;
    this.recipeList = recipeList;
  }

  @Override
  public int getCount() {
    return recipeList.size();
  }

  @Override
  public Recipe getItem(int position) {
    return recipeList.get(position);
  }

  @Override
  public long getItemId(int position) {
    return getItem(position).id;
  }

  @Override
  public View getView(int position, View convertView, ViewGroup parent) {
    if (convertView == null) {
      convertView = LayoutInflater.from(context).inflate(layout, parent, false);
    }
    ImageView image = convertView.findViewById(R.id.photo);
    TextView title = convertView.findViewById(R.id.title);
    TextView meta = convertView.findViewById(R.id.meta);
    Recipe recipe = getItem(position);
    title.setText(recipe.title);
    meta.setText(
        (recipe.favorite ? "♥ · " : "")
            + (recipe.catalogKey == null ? recipe.cookbook : "Wikibooks · CC BY-SA 4.0")
            + (recipe.servingsKnown ? " · " + Units.format(recipe.servings) + " phần" : ""));
    Ui.image(image, recipe.image);
    return convertView;
  }
}
