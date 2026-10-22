package com.example.recime;

import static org.junit.Assert.*;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.recime.helper.CatalogImporter;
import com.example.recime.model.Recipe;
import com.example.recime.sql.LabDatabase;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CatalogDatabaseTest {
  @Test
  public void offlineCatalogInstallsIdempotentlyAndPreservesUserEdits() throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    LabDatabase database = new LabDatabase(context, null);
    try {
      int inserted = CatalogImporter.install(context, database);
      assertEquals(547, inserted);
      assertEquals(547, database.dao().recipeCount());
      Recipe recipe = database.dao().recipeSnapshot().get(0);
      recipe.title = "Món tôi đã sửa";
      recipe.favorite = true;
      database.dao().update(recipe);
      assertEquals(0, CatalogImporter.install(context, database));
      assertEquals("Món tôi đã sửa", database.dao().find(recipe.id).title);
      assertTrue(database.dao().find(recipe.id).favorite);
      assertTrue(
          database.dao().recipeSnapshot().stream()
              .anyMatch(r -> r.image.startsWith("file:///android_asset/")));
      database.deleteRecipe(recipe);
      assertNull(database.dao().find(recipe.id));
      assertEquals(0, CatalogImporter.install(context, database));
      assertNull(database.dao().findCatalog(recipe.catalogKey));
      assertEquals(546, database.dao().recipeCount());
    } finally {
      database.close();
    }
  }

  @Test
  public void migrationOneToThreeKeepsRecipesAndMealForeignKeys() {
    Context context = ApplicationProvider.getApplicationContext();
    String name = "migration-" + System.nanoTime() + ".db";
    java.io.File file = context.getDatabasePath(name);
    file.getParentFile().mkdirs();
    android.database.sqlite.SQLiteDatabase old =
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(file, null);
    try {
      old.execSQL(
          "CREATE TABLE recipes (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,title"
              + " TEXT,ingredients TEXT,steps TEXT,cookbook TEXT,tags TEXT,source TEXT,image"
              + " TEXT,servings REAL NOT NULL,favorite INTEGER NOT NULL,calories REAL,protein"
              + " REAL,carbs REAL,fat REAL,nutritionSource TEXT)");
      old.execSQL(
          "CREATE TABLE meals (slot TEXT NOT NULL PRIMARY KEY,day TEXT,type TEXT,recipeId INTEGER"
              + " NOT NULL,FOREIGN KEY(recipeId) REFERENCES recipes(id) ON UPDATE NO ACTION ON"
              + " DELETE CASCADE)");
      old.execSQL("CREATE INDEX index_meals_recipeId ON meals(recipeId)");
      old.execSQL(
          "CREATE TABLE groceries (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,name TEXT,unit"
              + " TEXT,category TEXT,amount REAL,checked INTEGER NOT NULL)");
      old.execSQL(
          "INSERT INTO recipes"
              + " (id,title,ingredients,steps,cookbook,tags,source,image,servings,favorite,nutritionSource)"
              + " VALUES(1,'Món riêng','200 g đậu','Nấu chín','Nhà','','','',2,1,'')");
      old.execSQL("INSERT INTO meals VALUES('2026-10-22|Sáng','2026-10-22','Sáng',1)");
      old.setVersion(1);
    } finally {
      old.close();
    }
    LabDatabase upgraded = new LabDatabase(context, name);
    try {
      Recipe r = upgraded.dao().find(1);
      assertEquals("Món riêng", r.title);
      assertTrue(r.favorite);
      assertTrue(r.servingsKnown);
      assertNull(r.catalogKey);
      assertEquals(1, upgraded.dao().mealSnapshot("2026-10-19", "2026-10-25").size());
    } finally {
      upgraded.close();
      context.deleteDatabase(name);
    }
  }
}
