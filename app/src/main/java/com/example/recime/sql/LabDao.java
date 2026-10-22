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
import com.example.recime.helper.RecipeParser;
import com.example.recime.helper.GroceryAggregator;
import com.example.recime.helper.Units;
import com.example.recime.model.Ingredient;
import java.util.ArrayList;
import java.util.List;

/** DAO viết tay: ContentValues để ghi, Cursor để đọc, giống LopHocDAO. */
public class LabDao {
  private final LabDatabase helper;
  private final MutableLiveData<List<Recipe>> recipeData = new MutableLiveData<>(new ArrayList<>());
  private final MutableLiveData<List<Meal>> mealData = new MutableLiveData<>(new ArrayList<>());
  private final MutableLiveData<List<String>> cookbookData = new MutableLiveData<>(new ArrayList<>());
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

  public LiveData<List<String>> cookbooks() { return cookbookData; }

  public LiveData<List<Grocery>> groceries() {
    return groceryData;
  }

  public void refresh() {
    if (helper.getWritableDatabase().inTransaction()) return;
    recipeData.postValue(
        getRecipes("SELECT * FROM recipes ORDER BY (image != '') DESC, favorite DESC, id DESC"));
    mealData.postValue(allMeals(null,null));
    List<Grocery> items = getGroceries("SELECT * FROM groceries ORDER BY category,name,id");
    items.addAll(plannedGroceries());
    items.sort((a,b) -> {
      if (a.checked != b.checked) return a.checked ? 1 : -1;
      return (a.category + a.name).compareToIgnoreCase(b.category + b.name);
    });
    groceryData.postValue(items);
    cookbookData.postValue(bookNamesSnapshot());
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
    attachCookbook(recipe,id);
    refresh();
    return id;
  }

  public long insertCatalog(Recipe recipe) {
    long id =
        helper
            .getWritableDatabase()
            .insertWithOnConflict(
                "recipes", null, recipeValues(recipe), SQLiteDatabase.CONFLICT_IGNORE);
    if (id > 0) attachCookbook(recipe,id);
    refresh();
    return id;
  }

  public void update(Recipe recipe) {
    helper
        .getWritableDatabase()
        .update("recipes", recipeValues(recipe), "id=?", new String[] {String.valueOf(recipe.id)});
    attachCookbook(recipe, recipe.id);
    syncPlanGroceries();
    refresh();
  }

  public void delete(Recipe recipe) {
    helper
        .getWritableDatabase()
        .delete("recipes", "id=?", new String[] {String.valueOf(recipe.id)});
    syncPlanGroceries();
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

  // Keep legacy one-recipe-per-slot records for lossless Room v1-3 migration.
  // Extra meals are in a separate v4 table, so multiple dishes can share a slot.
  public List<Meal> mealSnapshot(String start, String end) {
    return allMeals(start,end);
  }

  private List<Meal> allMeals(String start,String end) {
    List<Meal> list = getMeals(start == null
        ? "SELECT * FROM meals ORDER BY day,type"
        : "SELECT * FROM meals WHERE day>=? AND day<=? ORDER BY day,type",
        start == null ? new String[]{} : new String[]{start,end});
    String sql = "SELECT id,day,type,recipeId,servings FROM meal_extras";
    String[] args = {};
    if (start != null) {
      sql += " WHERE day>=? AND day<=?";
      args = new String[]{start,end};
    }
    sql += " ORDER BY day,type,position,id";
    try(Cursor cursor=helper.getReadableDatabase().rawQuery(sql,args)){
      while(cursor.moveToNext()){
        Meal m=new Meal();
        m.extraId=cursor.getLong(0);
        m.day=text(cursor,"day"); m.type=text(cursor,"type");
        m.slot=m.day+"|"+m.type; m.recipeId=cursor.getLong(3);
        m.servings=cursor.getDouble(4);
        list.add(m);
      }
    }
    return list;
  }

  public void putMeal(Meal meal) {
    ContentValues values = new ContentValues();
    values.put("slot", meal.slot);
    values.put("day", meal.day);
    values.put("type", meal.type);
    values.put("recipeId", meal.recipeId);
    helper.getWritableDatabase().insertWithOnConflict("meals",null,values,SQLiteDatabase.CONFLICT_REPLACE);
    syncPlanGroceries();
    refresh();
  }

  public long addMeal(Meal meal) {
    ContentValues values=new ContentValues();
    values.put("day",meal.day); values.put("type",meal.type);
    values.put("recipeId",meal.recipeId);
    values.put("servings",meal.servings > 0 ? meal.servings : 0.0);
    long id=helper.getWritableDatabase().insertOrThrow("meal_extras",null,values);
    syncPlanGroceries();
    refresh();
    return id;
  }

  public void deleteMeal(Meal meal) {
    if(meal.extraId > 0)
      helper.getWritableDatabase().delete("meal_extras","id=?",new String[]{String.valueOf(meal.extraId)});
    else helper.getWritableDatabase().delete("meals","slot=?",new String[]{meal.slot});
    syncPlanGroceries();
    refresh();
  }

  private List<Meal> getMeals(String sql,String... args) {
    List<Meal> list=new ArrayList<>();
    try(Cursor cursor=helper.getReadableDatabase().rawQuery(sql,args)){
      while(cursor.moveToNext()){
        Meal m=new Meal();
        m.slot=text(cursor,"slot");m.day=text(cursor,"day");
        m.type=text(cursor,"type");m.recipeId=cursor.getLong(cursor.getColumnIndexOrThrow("recipeId"));
        list.add(m);
      }
    }
    return list;
  }

  public void clearMealWeek(String start,String end) {
    helper.getWritableDatabase().delete("meals","day>=? AND day<=?",new String[]{start,end});
    helper.getWritableDatabase().delete("meal_extras","day>=? AND day<=?",new String[]{start,end});
    syncPlanGroceries();
    refresh();
  }

  /** Fully recompute derived rows. Manual groceries are NEVER altered. */
  public void syncPlanGroceries(){
    SQLiteDatabase db=helper.getWritableDatabase();
    java.util.Map<String,Boolean> checked=new java.util.HashMap<>();
    try(Cursor c=db.rawQuery("SELECT itemKey,checked FROM plan_groceries",null)){
      while(c.moveToNext()) checked.put(c.getString(0),c.getInt(1)!=0);
    }
    List<Ingredient> items=new ArrayList<>();
    for(Meal m:allMeals(null,null)){
      Recipe r=find(m.recipeId);
      if(r!=null) {
        double ratio= m.extraId > 0 && m.servings > 0 && r.servings > 0
            ? m.servings/r.servings : 1.0;
        for(Ingredient ingredient:RecipeParser.ingredients(r.ingredients))
          items.add(ingredient.scale(ratio));
      }
    }
    List<Ingredient> merged=GroceryAggregator.merge(items,5,15,240);
    db.beginTransaction();
    try {
      db.delete("plan_groceries",null,null);
      for(Ingredient item:merged){
        String key=Units.key(item.name)+"|"+Units.canonical(item.unit);
        ContentValues v=new ContentValues();
        v.put("itemKey",key);
        v.put("name",item.name);
        v.put("amount",item.amount);
        v.put("unit",item.unit);
        v.put("category",GroceryAggregator.category(item.name));
        v.put("checked",Boolean.TRUE.equals(checked.get(key))?1:0);
        db.insertWithOnConflict("plan_groceries",null,v,SQLiteDatabase.CONFLICT_REPLACE);
      }
      db.setTransactionSuccessful();
    } finally {db.endTransaction();}
  }

  public List<Grocery> plannedGrocerySnapshot(){return plannedGroceries();}

  private List<Grocery> plannedGroceries(){
    List<Grocery> list=new ArrayList<>();
    try(Cursor c=helper.getReadableDatabase().rawQuery("SELECT * FROM plan_groceries ORDER BY category,name",null)){
      while(c.moveToNext()){
        Grocery g=new Grocery();
        g.id=-c.getLong(c.getColumnIndexOrThrow("id"));
        g.planned=true;
        g.name=text(c,"name");g.unit=text(c,"unit");g.category=text(c,"category");
        g.amount=number(c,"amount");g.checked=c.getInt(c.getColumnIndexOrThrow("checked"))!=0;
        list.add(g);
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
    if (grocery.planned) {
      ContentValues v=new ContentValues(); v.put("checked",grocery.checked?1:0);
      helper.getWritableDatabase().update("plan_groceries",v,"id=?",
          new String[]{String.valueOf(-grocery.id)});
      refresh();return;
    }
    helper
        .getWritableDatabase()
        .update(
            "groceries", groceryValues(grocery), "id=?", new String[] {String.valueOf(grocery.id)});
    refresh();
  }

  public void deleteGrocery(Grocery grocery) {
    if(grocery.planned) return;
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

  /** True cookbook entities; old recipe.cookbook remains the primary category for legacy code. */
  private void attachCookbook(Recipe recipe,long id) {
    String name=recipe.cookbook==null?"":recipe.cookbook.trim();
    if(name.isEmpty()) name="Món nhà";
    ContentValues book=new ContentValues();
    book.put("name",name);book.put("createdAt",System.currentTimeMillis());
    SQLiteDatabase db=helper.getWritableDatabase();
    db.insertWithOnConflict("cookbooks",null,book,SQLiteDatabase.CONFLICT_IGNORE);
    long bookId=-1;
    try(Cursor c=db.rawQuery("SELECT id FROM cookbooks WHERE name=? COLLATE NOCASE",
        new String[]{name})){if(c.moveToFirst()) bookId=c.getLong(0);}
    if(bookId<=0)return;
    db.delete("cookbook_members","recipeId=?",new String[]{String.valueOf(id)});
    ContentValues member=new ContentValues();
    member.put("cookbookId",bookId);member.put("recipeId",id);
    db.insertWithOnConflict("cookbook_members",null,member,SQLiteDatabase.CONFLICT_IGNORE);
  }

  public List<String> bookNamesSnapshot(){
    List<String> names=new ArrayList<>();
    try(Cursor c=helper.getReadableDatabase().rawQuery(
        "SELECT name FROM cookbooks ORDER BY name COLLATE NOCASE",null)){
      while(c.moveToNext()) names.add(c.getString(0));
    }
    return names;
  }

  public void createCookbook(String name){
    String value=name==null?"":name.trim();
    if(value.isEmpty())throw new IllegalArgumentException("Tên cookbook không được trống");
    ContentValues v=new ContentValues();
    v.put("name",value);v.put("createdAt",System.currentTimeMillis());
    helper.getWritableDatabase().insertWithOnConflict("cookbooks",null,v,SQLiteDatabase.CONFLICT_IGNORE);
    refresh();
  }

  public void renameCookbook(String oldName,String newName){
    String value=newName==null?"":newName.trim();
    if(value.isEmpty())throw new IllegalArgumentException("Tên cookbook không được trống");
    SQLiteDatabase db=helper.getWritableDatabase();
    db.beginTransaction();
    try{
      ContentValues v=new ContentValues();v.put("name",value);
      if(db.update("cookbooks",v,"name=? COLLATE NOCASE",new String[]{oldName})!=1)
        throw new IllegalArgumentException("Không tìm thấy cookbook hoặc tên đã tồn tại");
      ContentValues r=new ContentValues();r.put("cookbook",value);
      db.update("recipes",r,"cookbook=? COLLATE NOCASE",new String[]{oldName});
      db.setTransactionSuccessful();
    }finally{db.endTransaction();}
    refresh();
  }

  public void deleteCookbook(String name){
    if("Món nhà".equalsIgnoreCase(name))throw new IllegalArgumentException("Không thể xóa bộ sưu tập mặc định");
    SQLiteDatabase db=helper.getWritableDatabase();
    db.beginTransaction();
    try{
      ContentValues v=new ContentValues();v.put("cookbook","Món nhà");
      db.update("recipes",v,"cookbook=? COLLATE NOCASE",new String[]{name});
      db.delete("cookbooks","name=? COLLATE NOCASE",new String[]{name});
      ContentValues def=new ContentValues();def.put("name","Món nhà");
      db.insertWithOnConflict("cookbooks",null,def,SQLiteDatabase.CONFLICT_IGNORE);
      db.execSQL("INSERT OR IGNORE INTO cookbook_members(cookbookId,recipeId)"+
        " SELECT c.id,r.id FROM recipes r JOIN cookbooks c ON c.name=r.cookbook");
      db.setTransactionSuccessful();
    }finally{db.endTransaction();}
    refresh();
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
