package com.example.recime.helper;

import com.example.recime.model.Recipe;
import java.util.*;

/** Ordered selection independent of a View; legacy covers are resolved by Recipe. */
public final class RecipeGallery {
  private List<String> paths = new ArrayList<>();
  private int index;
  private String selected = "";
  public void restore(int position, String path) { index = Math.max(0, position); selected = path; }
  public void update(Recipe recipe) {
    List<String> next = recipe.photoPaths();
    paths = new ArrayList<>(next.subList(0, Math.min(5, next.size())));
    int retained = paths.indexOf(selected);
    index = retained >= 0 ? retained : Math.min(index, Math.max(0, paths.size() - 1));
    selected = paths.isEmpty() ? "" : paths.get(index);
  }
  public void move(int offset) {
    index = Math.max(0, Math.min(Math.max(0, paths.size() - 1), index + offset));
    selected = paths.isEmpty() ? "" : paths.get(index);
  }
  public int index() { return index; }
  public int count() { return paths.size(); }
  public String path() { return selected; }
}
