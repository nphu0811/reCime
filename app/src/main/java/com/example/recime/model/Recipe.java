package com.example.recime.model;

public class Recipe {
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
