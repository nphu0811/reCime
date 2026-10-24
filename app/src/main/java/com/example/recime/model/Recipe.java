package com.example.recime.model;

public class Recipe {
  public Recipe() {}

  /** Detached editor snapshot: an invalid draft must never mutate observed library data. */
  public Recipe(Recipe other) {
    id = other.id;
    title = other.title; ingredients = other.ingredients; steps = other.steps;
    cookbook = other.cookbook; tags = other.tags; source = other.source; image = other.image;
    servings = other.servings; favorite = other.favorite;
    calories = other.calories; protein = other.protein; carbs = other.carbs; fat = other.fat;
    nutritionSource = other.nutritionSource; catalogKey = other.catalogKey;
    attribution = other.attribution; imageAttribution = other.imageAttribution;
    servingsKnown = other.servingsKnown;
    images = new java.util.ArrayList<>(other.images);
  }

  public long id;

  public String title = "",
      ingredients = "",
      steps = "",
      cookbook = "Món nhà",
      tags = "",
      source = "",
      image = "";
  public double servings = 2;
  public boolean favorite = false;
  // Manual values per original serving, never guessed from ingredient text.
  public Double calories, protein, carbs, fat;
  public String nutritionSource = "";

  public String catalogKey;

  public String attribution = "";

  public String imageAttribution = "";

  public boolean servingsKnown = true;

  public java.util.List<String> images = new java.util.ArrayList<>();

  /** Legacy recipes keep their existing single cover until explicitly edited. */
  public java.util.List<String> photoPaths() {
    if (!images.isEmpty()) return new java.util.ArrayList<>(images);
    return image == null || image.isEmpty() ? new java.util.ArrayList<>()
        : new java.util.ArrayList<>(java.util.Collections.singletonList(image));
  }

  public void setPhotoPaths(java.util.List<String> paths) {
    if (paths.size() > 5) throw new IllegalArgumentException("Tối đa 5 ảnh mỗi công thức");
    images = new java.util.ArrayList<>(paths);
    image = images.isEmpty() ? "" : images.get(0);
  }
}
