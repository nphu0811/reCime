package com.example.recime.data;

import androidx.room.*;

@Database(
    entities = {Recipe.class, Meal.class, Grocery.class},
    version = 1,
    exportSchema = true)
public abstract class LabDatabase extends RoomDatabase {
  public abstract LabDao dao();
}
