package com.example.recime.sql;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import com.example.recime.model.CatalogTombstone;
import com.example.recime.model.Recipe;

/** SQLiteOpenHelper như project QuanLySinhVien. Giữ tên file và version cũ để bảo toàn dữ liệu. */
public class LabDatabase extends SQLiteOpenHelper {
  public static final String DATABASE_NAME = "myrecipelab.db";
  public static final int DATABASE_VERSION = 3;
  private final LabDao dao;

  public LabDatabase(Context context) {
    this(context, DATABASE_NAME);
  }

  // name=null dùng SQLite trong bộ nhớ cho test, không ảnh hưởng database của app.
  public LabDatabase(Context context, String name) {
    super(context.getApplicationContext(), name, null, DATABASE_VERSION);
    dao = new LabDao(this);
  }

  @Override
  public void onConfigure(SQLiteDatabase db) {
    super.onConfigure(db);
    db.setForeignKeyConstraintsEnabled(true);
  }

  @Override
  public void onCreate(SQLiteDatabase db) {
    db.execSQL(
        "CREATE TABLE IF NOT EXISTS `recipes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,"
            + " `title` TEXT, `ingredients` TEXT, `steps` TEXT, `cookbook` TEXT, `tags` TEXT,"
            + " `source` TEXT, `image` TEXT, `servings` REAL NOT NULL, `favorite` INTEGER NOT NULL,"
            + " `calories` REAL, `protein` REAL, `carbs` REAL, `fat` REAL, `nutritionSource` TEXT,"
            + " `catalogKey` TEXT, `attribution` TEXT DEFAULT '', `imageAttribution` TEXT DEFAULT"
            + " '', `servingsKnown` INTEGER NOT NULL DEFAULT 1)");
    db.execSQL(
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_recipes_catalogKey` ON `recipes` (`catalogKey`)");
    db.execSQL(
        "CREATE TABLE IF NOT EXISTS `meals` (`slot` TEXT NOT NULL, `day` TEXT, `type` TEXT,"
            + " `recipeId` INTEGER NOT NULL, PRIMARY KEY(`slot`), FOREIGN KEY(`recipeId`)"
            + " REFERENCES `recipes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_meals_recipeId` ON `meals` (`recipeId`)");
    db.execSQL(
        "CREATE TABLE IF NOT EXISTS `groceries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,"
            + " `name` TEXT, `unit` TEXT, `category` TEXT, `amount` REAL, `checked` INTEGER NOT"
            + " NULL)");
    db.execSQL(
        "CREATE TABLE IF NOT EXISTS `catalog_tombstones` (`catalogKey` TEXT NOT NULL, `deletedAt`"
            + " INTEGER NOT NULL, PRIMARY KEY(`catalogKey`))");
  }

  @Override
  public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    // Chỉ bổ sung cột/bảng; không DROP bảng chứa dữ liệu người dùng.
    if (oldVersion < 2) {
      db.execSQL("ALTER TABLE recipes ADD COLUMN catalogKey TEXT");
      db.execSQL("ALTER TABLE recipes ADD COLUMN attribution TEXT DEFAULT ''");
      db.execSQL("ALTER TABLE recipes ADD COLUMN imageAttribution TEXT DEFAULT ''");
      db.execSQL("ALTER TABLE recipes ADD COLUMN servingsKnown INTEGER NOT NULL DEFAULT 1");
      db.execSQL(
          "CREATE UNIQUE INDEX IF NOT EXISTS index_recipes_catalogKey ON recipes(catalogKey)");
    }
    if (oldVersion < 3) {
      db.execSQL(
          "CREATE TABLE IF NOT EXISTS catalog_tombstones (catalogKey TEXT NOT NULL PRIMARY KEY,"
              + " deletedAt INTEGER NOT NULL)");
    }
  }

  public LabDao dao() {
    return dao;
  }

  public void runInTransaction(Runnable action) {
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      action.run();
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
    dao.refresh();
  }

  public void deleteRecipe(Recipe recipe) {
    runInTransaction(
        () -> {
          if (recipe.catalogKey != null) {
            CatalogTombstone deleted = new CatalogTombstone();
            deleted.catalogKey = recipe.catalogKey;
            deleted.deletedAt = System.currentTimeMillis();
            dao.putTombstone(deleted);
          }
          dao.delete(recipe);
        });
  }
}
