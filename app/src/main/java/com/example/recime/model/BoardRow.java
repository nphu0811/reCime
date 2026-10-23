package com.example.recime.model;

/** Presentation data for one meal-planner or grocery row. */
public class BoardRow {
  public String title, meta, day, type;
  public boolean dayHeader, groupHeader;
  public boolean isHeader(){return dayHeader||groupHeader;}
  public Meal meal;
  public Grocery grocery;
}
