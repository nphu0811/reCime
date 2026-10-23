package com.example.recime.model;

public class Meal {
  public Meal() {}
  public Meal(Meal other) {
    slot=other.slot;day=other.day;type=other.type;recipeId=other.recipeId;
    extraId=other.extraId;servings=other.servings;
  }
  public String slot = "";
  public String day = "", type = "";
  public long recipeId;
  public long extraId;
  public double servings = 0; // 0 = original recipe yield
}
