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
}
