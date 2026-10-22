package com.example.recime;

import static org.junit.Assert.*;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.recime.model.Grocery;
import com.example.recime.model.Meal;
import com.example.recime.model.Recipe;
import com.example.recime.sql.LabDatabase;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class LabDatabaseTest {
  @Test
  public void persistsRecipeMealAndChecklistAndCascadesDelete() {
    Context context = ApplicationProvider.getApplicationContext();
    String name = "test-" + System.nanoTime() + ".db";
    LabDatabase db = new LabDatabase(context, name);
    try {
      Recipe r = new Recipe();
      r.title = "Test đậu";
      r.ingredients = "1 kg đậu";
      r.steps = "Nấu";
      long id = db.dao().insert(r);
      r.id = id;
      Meal m = new Meal();
      m.slot = "2026-10-22|Sáng";
      m.day = "2026-10-22";
      m.type = "Sáng";
      m.recipeId = id;
      db.dao().putMeal(m);
      Grocery g = new Grocery();
      g.name = "đậu";
      g.amount = 1000.0;
      g.unit = "g";
      g.category = "Đạm";
      g.id = db.dao().insertGrocery(g);
      g.checked = true;
      db.dao().updateGrocery(g);
      db.close();
      db = new LabDatabase(context, name);
      assertEquals("Test đậu", db.dao().find(id).title);
      assertEquals(1, db.dao().mealSnapshot("2026-10-19", "2026-10-25").size());
      assertTrue(db.dao().grocerySnapshot().get(0).checked);
      r.title = "Test đã sửa";
      db.dao().update(r);
      assertEquals(r.title, db.dao().find(id).title);
      db.dao().delete(r);
      assertNull(db.dao().find(id));
      assertTrue(db.dao().mealSnapshot("2026-10-19", "2026-10-25").isEmpty());
      db.dao().deleteGrocery(g);
      assertTrue(db.dao().grocerySnapshot().isEmpty());
    } finally {
      db.close();
      context.deleteDatabase(name);
    }
  }

  @Test
  public void formerRoomVersionThreeFileKeepsDataWithNativeHelper() {
    Context context = ApplicationProvider.getApplicationContext();
    String name = "legacy-room-" + System.nanoTime() + ".db";
    java.io.File file = context.getDatabasePath(name);
    file.getParentFile().mkdirs();
    android.database.sqlite.SQLiteDatabase legacy =
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(file, null);
    try {
      legacy.execSQL(
          "CREATE TABLE IF NOT EXISTS `recipes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,"
              + " `title` TEXT, `ingredients` TEXT, `steps` TEXT, `cookbook` TEXT, `tags` TEXT,"
              + " `source` TEXT, `image` TEXT, `servings` REAL NOT NULL, `favorite` INTEGER NOT"
              + " NULL, `calories` REAL, `protein` REAL, `carbs` REAL, `fat` REAL,"
              + " `nutritionSource` TEXT, `catalogKey` TEXT, `attribution` TEXT DEFAULT '',"
              + " `imageAttribution` TEXT DEFAULT '', `servingsKnown` INTEGER NOT NULL DEFAULT 1)");
      legacy.execSQL(
          "CREATE UNIQUE INDEX IF NOT EXISTS `index_recipes_catalogKey` ON `recipes`"
              + " (`catalogKey`)");
      legacy.execSQL(
          "CREATE TABLE IF NOT EXISTS `meals` (`slot` TEXT NOT NULL, `day` TEXT, `type` TEXT,"
              + " `recipeId` INTEGER NOT NULL, PRIMARY KEY(`slot`), FOREIGN KEY(`recipeId`)"
              + " REFERENCES `recipes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
      legacy.execSQL("CREATE INDEX IF NOT EXISTS `index_meals_recipeId` ON `meals` (`recipeId`)");
      legacy.execSQL(
          "CREATE TABLE IF NOT EXISTS `groceries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,"
              + " `name` TEXT, `unit` TEXT, `category` TEXT, `amount` REAL, `checked` INTEGER NOT"
              + " NULL)");
      legacy.execSQL(
          "CREATE TABLE IF NOT EXISTS `catalog_tombstones` (`catalogKey` TEXT NOT NULL, `deletedAt`"
              + " INTEGER NOT NULL, PRIMARY KEY(`catalogKey`))");
      legacy.execSQL(
          "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash"
              + " TEXT)");
      legacy.execSQL("INSERT INTO room_master_table VALUES(42,'63683bc4307ff1973c47265bb4488e0d')");
      legacy.execSQL(
          "INSERT INTO recipes"
              + " (id,title,ingredients,steps,servings,favorite,catalogKey,attribution)"
              + " VALUES(77,'Món đã lưu','200 g gạo','Nấu',2,1,'legacy-key','Nguồn cũ')");
      legacy.execSQL("INSERT INTO meals VALUES('legacy-slot','2026-10-22','Trưa',77)");
      legacy.execSQL(
          "INSERT INTO groceries (name,unit,category,amount,checked)"
              + " VALUES('gạo','g','Khô',200,1)");
      legacy.execSQL("INSERT INTO catalog_tombstones VALUES('deleted-key',1)");
      legacy.setVersion(3);
    } finally {
      legacy.close();
    }
    LabDatabase helper = new LabDatabase(context, name);
    try {
      Recipe recipe = helper.dao().find(77);
      assertEquals("Món đã lưu", recipe.title);
      assertTrue(recipe.favorite);
      assertEquals("Nguồn cũ", recipe.attribution);
      assertEquals(1, helper.dao().mealSnapshot("2026-10-19", "2026-10-25").size());
      assertTrue(helper.dao().grocerySnapshot().get(0).checked);
      assertEquals(1, helper.dao().wasCatalogDeleted("deleted-key"));
      recipe.title = "Món sửa sau nâng cấp";
      helper.dao().update(recipe);
      assertEquals(recipe.title, helper.dao().find(77).title);
    } finally {
      helper.close();
      context.deleteDatabase(name);
    }
  }

  @Test
  public void failedTransactionRollsBackAllInsertedRows() {
    Context context = ApplicationProvider.getApplicationContext();
    LabDatabase helper = new LabDatabase(context, null);
    try {
      try {
        helper.runInTransaction(
            () -> {
              Recipe recipe = new Recipe();
              recipe.title = "Không được lưu";
              helper.dao().insert(recipe);
              throw new IllegalStateException("intentional rollback");
            });
        fail("Transaction must propagate failure");
      } catch (IllegalStateException expected) {
        assertEquals("intentional rollback", expected.getMessage());
      }
      assertEquals(0, helper.dao().recipeCount());
    } finally {
      helper.close();
    }
  }
}
