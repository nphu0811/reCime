package com.example.recime.sql;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.recime.model.Recipe;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Room v2 -> SQLiteOpenHelper v4 regression test.
 * The old table definitions and identity hash match app/schemas/.../2.json.
 * Uses a uniquely named TEST database, never the user's myrecipelab.db.
 */
@RunWith(AndroidJUnit4.class)
public class LegacyRoomV2MigrationTest {
  @Test
  public void upgradingV2KeepsUserDataAndEnablesDeletionTombstones() {
    Context context = ApplicationProvider.getApplicationContext();
    String name = "legacy-room-v2-" + System.nanoTime() + ".db";
    java.io.File file = context.getDatabasePath(name);
    assertNotNull(file.getParentFile());
    assertTrue(file.getParentFile().isDirectory() || file.getParentFile().mkdirs());

    SQLiteDatabase old = SQLiteDatabase.openOrCreateDatabase(file, null);
    try {
      old.execSQL(
          "CREATE TABLE recipes (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,"
              + " title TEXT, ingredients TEXT, steps TEXT, cookbook TEXT, tags TEXT,"
              + " source TEXT, image TEXT, servings REAL NOT NULL, favorite INTEGER NOT NULL,"
              + " calories REAL, protein REAL, carbs REAL, fat REAL, nutritionSource TEXT,"
              + " catalogKey TEXT, attribution TEXT DEFAULT '', imageAttribution TEXT DEFAULT '',"
              + " servingsKnown INTEGER NOT NULL DEFAULT 1)");
      old.execSQL("CREATE UNIQUE INDEX index_recipes_catalogKey ON recipes(catalogKey)");
      old.execSQL(
          "CREATE TABLE meals (slot TEXT NOT NULL, day TEXT, type TEXT, recipeId INTEGER"
              + " NOT NULL, PRIMARY KEY(slot), FOREIGN KEY(recipeId) REFERENCES recipes(id)"
              + " ON UPDATE NO ACTION ON DELETE CASCADE)");
      old.execSQL("CREATE INDEX index_meals_recipeId ON meals(recipeId)");
      old.execSQL(
          "CREATE TABLE groceries (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,"
              + " name TEXT, unit TEXT, category TEXT, amount REAL, checked INTEGER NOT NULL)");
      old.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)");
      old.execSQL(
          "INSERT INTO room_master_table VALUES"
              + " (42,'88b301e5e322600733d52ba4a1261b75')");

      old.execSQL(
          "INSERT INTO recipes"
              + " (id,title,ingredients,steps,cookbook,servings,favorite,catalogKey,attribution)"
              + " VALUES(91,'Bún tự nhập','200 g bún','Nấu','Nhà',2,1,"
              + " 'legacy-v2-91','Ghi chú tác giả')");
      old.execSQL("INSERT INTO meals VALUES('2026-10-23:lunch','2026-10-23','Trưa',91)");
      old.execSQL(
          "INSERT INTO groceries (id,name,unit,category,amount,checked)"
              + " VALUES(12,'Bún','g','Lương thực',200,1)");
      old.setVersion(2);
    } finally {
      old.close();
    }

    LabDatabase database = new LabDatabase(context, name);
    try {
      assertEquals(com.example.recime.sql.LabDatabase.DATABASE_VERSION, database.getReadableDatabase().getVersion());

      Recipe recipe = database.dao().find(91);
      assertNotNull(recipe);
      assertEquals("Bún tự nhập", recipe.title);
      assertEquals("Ghi chú tác giả", recipe.attribution);
      assertEquals("legacy-v2-91", recipe.catalogKey);
      assertTrue(recipe.favorite);
      assertTrue(recipe.servingsKnown);
      assertEquals(1, database.dao().mealSnapshot("2026-10-23", "2026-10-23").size());
      assertEquals(91, database.dao().mealSnapshot("2026-10-23", "2026-10-23")
          .get(0).recipeId);
      assertEquals(1, database.dao().grocerySnapshot().size());
      assertTrue(database.dao().grocerySnapshot().get(0).checked);
      assertEquals(12, database.dao().grocerySnapshot().get(0).id);
      assertTrue(exists(database.getReadableDatabase(), "catalog_tombstones"));
      assertTrue(exists(database.getReadableDatabase(), "room_master_table"));
      assertEquals(0, database.dao().wasCatalogDeleted("legacy-v2-91"));

      // Persist an edit and verify that reopening does not recreate the database.
      recipe.title = "Bún sau nâng cấp";
      database.dao().update(recipe);
      database.close();
      database = new LabDatabase(context, name);
      assertEquals("Bún sau nâng cấp", database.dao().find(91).title);

      // Recipe deletion should cascade to meal; groceries are independent.
      database.deleteRecipe(database.dao().find(91));
      assertTrue(database.dao().mealSnapshot("2026-10-23", "2026-10-23").isEmpty());
      assertEquals(1, database.dao().grocerySnapshot().size());
      assertEquals(1, database.dao().wasCatalogDeleted("legacy-v2-91"));

      database.close();
      database = new LabDatabase(context, name);
      assertEquals(1, database.dao().wasCatalogDeleted("legacy-v2-91"));
      assertFalse(exists(database.getReadableDatabase(), "missing_table"));
    } finally {
      database.close();
      context.deleteDatabase(name);
    }
  }

  private static boolean exists(SQLiteDatabase database, String table) {
    try (Cursor cursor = database.rawQuery(
        "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
        new String[] {table})) {
      return cursor.moveToFirst();
    }
  }
}
