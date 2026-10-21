package com.example.recime.data;

import androidx.room.*;

@Entity(tableName = "recipes")
public class Recipe {
  @PrimaryKey(autoGenerate = true)
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
}
