package com.example.recime.sql;

import static org.junit.Assert.*;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.recime.model.Recipe;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Frozen representative v4 tables; upgrade must preserve user rows and the legacy cover. */
@RunWith(AndroidJUnit4.class)
public class PhotoV5MigrationTest {
  @Test public void upgradingV4PreservesAllUserCollectionsAndAddsOrderedPhotos() {
    Context context = ApplicationProvider.getApplicationContext();
    String name = "photos-v4-" + System.nanoTime() + ".db";
    context.getDatabasePath(name).getParentFile().mkdirs();
    SQLiteDatabase old = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null);
    old.execSQL("CREATE TABLE recipes (id INTEGER PRIMARY KEY, title TEXT, ingredients TEXT, steps TEXT," +
        " cookbook TEXT,tags TEXT,source TEXT,image TEXT,servings REAL NOT NULL,favorite INTEGER NOT NULL," +
        " calories REAL,protein REAL,carbs REAL,fat REAL,nutritionSource TEXT,catalogKey TEXT," +
        " attribution TEXT,imageAttribution TEXT,servingsKnown INTEGER NOT NULL DEFAULT 1)");
    old.execSQL("CREATE TABLE meals (slot TEXT PRIMARY KEY,day TEXT,type TEXT,recipeId INTEGER NOT NULL)");
    old.execSQL("CREATE TABLE groceries (id INTEGER PRIMARY KEY,name TEXT,unit TEXT,category TEXT,amount REAL,checked INTEGER NOT NULL)");
    old.execSQL("CREATE TABLE catalog_tombstones (catalogKey TEXT PRIMARY KEY,deletedAt INTEGER NOT NULL)");
    old.execSQL("CREATE TABLE cookbooks (id INTEGER PRIMARY KEY,name TEXT NOT NULL COLLATE NOCASE UNIQUE,cover TEXT,createdAt INTEGER)");
    old.execSQL("CREATE TABLE cookbook_members (cookbookId INTEGER,recipeId INTEGER,PRIMARY KEY(cookbookId,recipeId))");
    old.execSQL("CREATE TABLE meal_extras (id INTEGER PRIMARY KEY,day TEXT,type TEXT,recipeId INTEGER,servings REAL,position INTEGER)");
    old.execSQL("CREATE TABLE plan_groceries (id INTEGER PRIMARY KEY,itemKey TEXT,name TEXT,unit TEXT,category TEXT,amount REAL,checked INTEGER)");
    old.execSQL("INSERT INTO recipes(id,title,ingredients,steps,cookbook,image,servings,favorite,attribution,servingsKnown)" +
        " VALUES(42,'Legacy recipe','200 g rice','Cook','Family','legacy-cover.jpg',2,1,'Credit retained',1)");
    old.execSQL("INSERT INTO meals VALUES('2030-01-01|Lunch','2030-01-01','Lunch',42)");
    old.execSQL("INSERT INTO groceries VALUES(11,'Manual','g','Other',10,1)");
    old.execSQL("INSERT INTO cookbooks VALUES(12,'Family','cover.jpg',123)");
    old.execSQL("INSERT INTO cookbook_members VALUES(12,42)");
    old.execSQL("INSERT INTO meal_extras VALUES(13,'2030-01-01','Lunch',42,4,0)");
    old.execSQL("INSERT INTO plan_groceries VALUES(14,'rice|g','rice','g','Other',400,1)");
    old.execSQL("INSERT INTO catalog_tombstones VALUES('deleted-key',456)");
    old.setVersion(4); old.close();
    LabDatabase db = new LabDatabase(context, name);
    try {
      assertEquals(5, db.getWritableDatabase().getVersion());
      Recipe recipe = db.dao().find(42);
      assertEquals("legacy-cover.jpg", recipe.image);
      assertEquals(Arrays.asList("legacy-cover.jpg"), recipe.photoPaths());
      assertTrue(recipe.favorite);
      assertEquals("Credit retained", recipe.attribution);
      assertEquals(2, db.dao().mealSnapshot("2030-01-01", "2030-01-01").size());
      assertEquals(1, db.dao().grocerySnapshot().size());
      assertTrue(db.dao().grocerySnapshot().get(0).checked);
      assertTrue(db.dao().recipeIdsForCookbook("Family").contains(42L));
      assertEquals(1, db.dao().wasCatalogDeleted("deleted-key"));
      recipe.setPhotoPaths(Arrays.asList("one", "two", "three", "four", "five"));
      db.dao().update(recipe);
      db.close(); db = new LabDatabase(context, name);
      assertEquals(recipe.photoPaths(), db.dao().find(42).photoPaths());
      assertEquals("one", db.dao().find(42).image);
      assertEquals(1, db.dao().grocerySnapshot().size());
      assertTrue(db.dao().find(42).favorite);
      // A move after migration must keep the occupied slot, legacy yield, images and collections.
      com.example.recime.model.Meal legacy=db.dao().mealSnapshot(null,null).stream()
          .filter(m->m.extraId==0).findFirst().get();
      assertEquals("Trưa",legacy.type); // Old English labels remain visible under the four UI categories.
      db.dao().moveMeal(legacy,"2030-01-02","Tối");
      assertEquals(1,db.dao().mealSnapshot("2030-01-01","2030-01-01").size());
      assertEquals(1,db.dao().mealSnapshot("2030-01-02","2030-01-02").size());
      assertEquals(600.0,db.dao().plannedGrocerySnapshot().get(0).amount,0.001);
      assertTrue(db.dao().plannedGrocerySnapshot().get(0).checked);
      db.close();db=new LabDatabase(context,name);
      assertEquals(recipe.photoPaths(),db.dao().find(42).photoPaths());
      assertEquals(2,db.dao().mealSnapshot(null,null).size());
      assertTrue(db.dao().grocerySnapshot().get(0).checked);
      assertTrue(db.dao().recipeIdsForCookbook("Family").contains(42L));
      assertEquals(1,db.dao().wasCatalogDeleted("deleted-key"));
    } finally { db.close(); context.deleteDatabase(name); }
  }
}
