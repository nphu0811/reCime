package com.example.recime.adapter;

import android.view.*;
import androidx.recyclerview.widget.RecyclerView;
import com.example.recime.R;
import com.example.recime.helper.Motion;
import com.example.recime.helper.Ui;
import com.example.recime.helper.Units;
import com.example.recime.model.Recipe;

public class RecipeAdapter extends RecyclerView.Adapter<RecipeAdapter.RecipeHolder> {
  private final java.util.List<Recipe> recipes;
  private final java.util.function.Consumer<Recipe> onClick;

  public RecipeAdapter(
      java.util.List<Recipe> recipes, java.util.function.Consumer<Recipe> onClick) {
    this.recipes = recipes;
    this.onClick = onClick;
  }

  public RecipeHolder onCreateViewHolder(ViewGroup p, int t) {
    return new RecipeHolder(
        LayoutInflater.from(p.getContext()).inflate(R.layout.item_recipe, p, false));
  }

  public int getItemCount() {
    return recipes.size();
  }

  public void onBindViewHolder(RecipeHolder h, int pos) {
    Recipe r = recipes.get(pos);
    Ui.set(h.itemView, R.id.title, r.title);
    Ui.set(
        h.itemView,
        R.id.meta,
        (r.favorite ? "♥ · " : "")
            + (r.catalogKey == null ? r.cookbook : "Wikibooks · CC BY-SA 4.0")
            + (r.servingsKnown ? " · " + Units.format(r.servings) + " phần" : ""));
    Ui.image(h.itemView.findViewById(R.id.photo), r.image);
    Motion.card(h.itemView);
    h.itemView.setOnClickListener(v -> onClick.accept(r));
  }

  static class RecipeHolder extends RecyclerView.ViewHolder {
    RecipeHolder(View v) {
      super(v);
    }
  }
}
