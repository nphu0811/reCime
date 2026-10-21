package com.example.recime.data;

import androidx.lifecycle.LiveData;
import androidx.room.*;
import java.util.List;

@Dao
public interface LabDao {
  @Query("SELECT * FROM recipes ORDER BY id DESC")
  LiveData<List<Recipe>> recipes();

  @Query("SELECT * FROM recipes ORDER BY id")
  List<Recipe> recipeSnapshot();

  @Query("SELECT * FROM recipes WHERE id=:id")
  Recipe find(long id);

  @Query("SELECT COUNT(*) FROM recipes")
  int recipeCount();

  @Insert
  long insert(Recipe r);

  @Update
  void update(Recipe r);

  @Delete
  void delete(Recipe r);

  @Query("SELECT * FROM meals ORDER BY day,type")
  LiveData<List<Meal>> meals();

  @Query("SELECT * FROM meals WHERE day>=:start AND day<=:end")
  List<Meal> mealSnapshot(String start, String end);

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  void putMeal(Meal m);

  @Delete
  void deleteMeal(Meal m);

  @Query("SELECT * FROM groceries ORDER BY category,name,id")
  LiveData<List<Grocery>> groceries();

  @Query("SELECT * FROM groceries ORDER BY id")
  List<Grocery> grocerySnapshot();

  @Insert
  long insertGrocery(Grocery g);

  @Update
  void updateGrocery(Grocery g);

  @Delete
  void deleteGrocery(Grocery g);
}
