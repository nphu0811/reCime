package com.example.recime.sql;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.example.recime.model.CatalogTombstone;
import com.example.recime.model.Grocery;
import com.example.recime.model.Meal;
import com.example.recime.model.Recipe;
import java.util.ArrayList;
import java.util.List;

/** DAO viết tay: ContentValues để ghi, Cursor để đọc, giống LopHocDAO. */
public class LabDao {
  private final LabDatabase helper;
  private final MutableLiveData<List<Recipe>> recipeData = new MutableLiveData<>(new ArrayList<>());
  private final MutableLiveData<List<Meal>> mealData = new MutableLiveData<>(new ArrayList<>());
  private final MutableLiveData<List<Grocery>> groceryData =
      new MutableLiveData<>(new ArrayList<>());

  public LabDao(LabDatabase helper) {
    this.helper = helper;
  }

  // LiveData chỉ báo cho màn hình nạp lại sau khi thay đổi; không sinh SQL tự động.
  public LiveData<List<Recipe>> recipes() {
    return recipeData;
  }

  public LiveData<List<Meal>> meals() {
    return mealData;
  }

  public LiveData<List<Grocery>> groceries() {
    return groceryData;
  }

  public void refresh() {
    if (helper.getWritableDatabase().inTransaction()) return;
    recipeData.postValue(
        getRecipes("SELECT * FROM recipes ORDER BY (image != '') DESC, favorite DESC, id DESC"));
    mealData.postValue(getMeals("SELECT * FROM meals ORDER BY day,type"));
    groceryData.postValue(getGroceries("SELECT * FROM groceries ORDER BY category,name,id"));
  }

  public List<Recipe> recipeSnapshot() {
    return getRecipes("SELECT * FROM recipes ORDER BY id");
  }

  public Recipe find(long id) {
    List<Recipe> list = getRecipes("SELECT * FROM recipes WHERE id=?", String.valueOf(id));
    return list.isEmpty() ? null : list.get(0);
  }

  public Recipe findCatalog(String key) {
    List<Recipe> list = getRecipes("SELECT * FROM recipes WHERE catalogKey=? LIMIT 1", key);
    return list.isEmpty() ? null : list.get(0);
  }

  public int recipeCount() {
    return count("SELECT COUNT(*) FROM recipes");
  }

  public int wasCatalogDeleted(String key) {
    return count("SELECT COUNT(*) FROM catalog_tombstones WHERE catalogKey=?", key);
  }

  private int count(String sql, String... args) {
    try (Cursor cursor = helper.getReadableDatabase().rawQuery(sql, args)) {
      return cursor.moveToFirst() ? cursor.getInt(0) : 0;
    }
  }

  public long insert(Recipe recipe) {
    long id = helper.getWritableDatabase().insertOrThrow("recipes", null, recipeValues(recipe));
    refresh();
    return id;
  }

  public long insertCatalog(Recipe recipe) {
    long id =
        helper
            .getWritableDatabase()
            .insertWithOnConflict(
                "recipes", null, recipeValues(recipe), SQLiteDatabase.CONFLICT_IGNORE);
    refresh();
    return id;
  }

  public void update(Recipe recipe) {
    helper
        .getWritableDatabase()
        .update("recipes", recipeValues(recipe), "id=?", new String[] {String.valueOf(recipe.id)});
    refresh();
  }

  public void delete(Recipe recipe) {
    helper
        .getWritableDatabase()
        .delete("recipes", "id=?", new String[] {String.valueOf(recipe.id)});
    refresh();
  }

  public void putTombstone(CatalogTombstone tombstone) {
    ContentValues values = new ContentValues();
    values.put("catalogKey", tombstone.catalogKey);
    values.put("deletedAt", tombstone.deletedAt);
    helper
        .getWritableDatabase()
        .insertWithOnConflict("catalog_tombstones", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    refresh();
  }

  private ContentValues recipeValues(Recipe recipe) {
    ContentValues values = new ContentValues();
    if (recipe.id != 0) values.put("id", recipe.id);
    values.put("title", recipe.title);
    values.put("ingredients", recipe.ingredients);
    values.put("steps", recipe.steps);
    values.put("cookbook", recipe.cookbook);
    values.put("tags", recipe.tags);
    values.put("source", recipe.source);
    values.put("image", recipe.image);
    values.put("servings", recipe.servings);
    values.put("favorite", recipe.favorite ? 1 : 0);
    values.put("calories", recipe.calories);
    values.put("protein", recipe.protein);
    values.put("carbs", recipe.carbs);
    values.put("fat", recipe.fat);
    values.put("nutritionSource", recipe.nutritionSource);
    values.put("catalogKey", recipe.catalogKey);
    values.put("attribution", recipe.attribution);
    values.put("imageAttribution", recipe.imageAttribution);
    values.put("servingsKnown", recipe.servingsKnown ? 1 : 0);
    return values;
  }

  private List<Recipe> getRecipes(String sql, String... args) {
    List<Recipe> list = new ArrayList<>();
    try (Cursor cursor = helper.getReadableDatabase().rawQuery(sql, args)) {
      while (cursor.moveToNext()) {
        Recipe recipe = new Recipe();
        recipe.id = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
        recipe.title = text(cursor, "title");
        recipe.ingredients = text(cursor, "ingredients");
        recipe.steps = text(cursor, "steps");
        recipe.cookbook = text(cursor, "cookbook");
        recipe.tags = text(cursor, "tags");
        recipe.source = text(cursor, "source");
        recipe.image = text(cursor, "image");
        recipe.servings = cursor.getDouble(cursor.getColumnIndexOrThrow("servings"));
        recipe.favorite = cursor.getInt(cursor.getColumnIndexOrThrow("favorite")) != 0;
        recipe.calories = number(cursor, "calories");
        recipe.protein = number(cursor, "protein");
        recipe.carbs = number(cursor, "carbs");
        recipe.fat = number(cursor, "fat");
        recipe.nutritionSource = text(cursor, "nutritionSource");
        recipe.catalogKey = cursor.getString(cursor.getColumnIndexOrThrow("catalogKey"));
        recipe.attribution = text(cursor, "attribution");
        recipe.imageAttribution = text(cursor, "imageAttribution");
        recipe.servingsKnown = cursor.getInt(cursor.getColumnIndexOrThrow("servingsKnown")) != 0;
        list.add(recipe);
      }
    }
    return list;
  }

  public List<Meal> mealSnapshot(String start, String end) {
    return getMeals("SELECT * FROM meals WHERE day>=? AND day<=?", start, end);
  }

  public void putMeal(Meal meal) {
    ContentValues values = new ContentValues();
    values.put("slot", meal.slot);
    values.put("day", meal.day);
    values.put("type", meal.type);
    values.put("recipeId", meal.recipeId);
    helper
        .getWritableDatabase()
        .insertWithOnConflict("meals", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    refresh();
  }

  public void deleteMeal(Meal meal) {
    helper.getWritableDatabase().delete("meals", "slot=?", new String[] {meal.slot});
    refresh();
  }

  private List<Meal> getMeals(String sql, String... args) {
    List<Meal> list = new ArrayList<>();
    try (Cursor cursor = helper.getReadableDatabase().rawQuery(sql, args)) {
      while (cursor.moveToNext()) {
        Meal meal = new Meal();
        meal.slot = text(cursor, "slot");
        meal.day = text(cursor, "day");
        meal.type = text(cursor, "type");
        meal.recipeId = cursor.getLong(cursor.getColumnIndexOrThrow("recipeId"));
        list.add(meal);
      }
    }
    return list;
  }

  public List<Grocery> grocerySnapshot() {
    return getGroceries("SELECT * FROM groceries ORDER BY id");
  }

  public long insertGrocery(Grocery grocery) {
    long id = helper.getWritableDatabase().insertOrThrow("groceries", null, groceryValues(grocery));
    refresh();
    return id;
  }

  public void updateGrocery(Grocery grocery) {
    helper
        .getWritableDatabase()
        .update(
            "groceries", groceryValues(grocery), "id=?", new String[] {String.valueOf(grocery.id)});
    refresh();
  }

  public void deleteGrocery(Grocery grocery) {
    helper
        .getWritableDatabase()
        .delete("groceries", "id=?", new String[] {String.valueOf(grocery.id)});
    refresh();
  }

  private ContentValues groceryValues(Grocery grocery) {
    ContentValues values = new ContentValues();
    if (grocery.id != 0) values.put("id", grocery.id);
    values.put("name", grocery.name);
    values.put("unit", grocery.unit);
    values.put("category", grocery.category);
    values.put("amount", grocery.amount);
    values.put("checked", grocery.checked ? 1 : 0);
    return values;
  }

  private List<Grocery> getGroceries(String sql, String... args) {
    List<Grocery> list = new ArrayList<>();
    try (Cursor cursor = helper.getReadableDatabase().rawQuery(sql, args)) {
      while (cursor.moveToNext()) {
        Grocery grocery = new Grocery();
        grocery.id = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
        grocery.name = text(cursor, "name");
        grocery.unit = text(cursor, "unit");
        grocery.category = text(cursor, "category");
        grocery.amount = number(cursor, "amount");
        grocery.checked = cursor.getInt(cursor.getColumnIndexOrThrow("checked")) != 0;
        list.add(grocery);
      }
    }
    return list;
  }

  private String text(Cursor cursor, String column) {
    String value = cursor.getString(cursor.getColumnIndexOrThrow(column));
    return value == null ? "" : value;
  }

  private Double number(Cursor cursor, String column) {
    int index = cursor.getColumnIndexOrThrow(column);
    return cursor.isNull(index) ? null : cursor.getDouble(index);
  }
}
