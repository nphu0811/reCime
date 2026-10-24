package com.example.recime;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.recime.helper.BackupStore;
import com.example.recime.model.*;
import com.example.recime.sql.*;
import java.io.*;
import java.util.*;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class BackupMigrationTest {
  @Test public void v5UpgradeAndPortableMergePreserveDataPhotosAndIdempotence() throws Exception {
    Context c = ApplicationProvider.getApplicationContext();
    String first = "backup-source-" + System.nanoTime() + ".db", second = "backup-target-" + System.nanoTime() + ".db";
    LabDatabase source = new LabDatabase(c, first), target = new LabDatabase(c, second);
    File photo = null;
    try {
      File dir = new File(c.getFilesDir(), "photos"); dir.mkdirs(); photo = File.createTempFile("backup_test_", ".png", dir);
      Bitmap bitmap = Bitmap.createBitmap(32,32, Bitmap.Config.ARGB_8888); bitmap.eraseColor(android.graphics.Color.GREEN);
      try (OutputStream out = new FileOutputStream(photo)) { bitmap.compress(Bitmap.CompressFormat.PNG,100,out); } bitmap.recycle();
      Recipe recipe = new Recipe(); recipe.title="Backup fixture"; recipe.ingredients="100 g rice"; recipe.steps="Cook\nServe"; recipe.source="https://example.org/qa";
      recipe.attribution="Test fixture CC0"; recipe.favorite=true; recipe.setPhotoPaths(Collections.singletonList(photo.getAbsolutePath())); recipe.id=source.dao().insert(recipe);
      source.dao().createCookbook("Empty fixture"); source.dao().createCookbook("Second fixture"); source.dao().addRecipeToCookbook(recipe.id,"Second fixture");
      Meal meal = new Meal(); meal.recipeId=recipe.id; meal.day="2026-10-24"; meal.type="Lunch"; meal.servings=4; source.dao().addMeal(meal);
      Grocery grocery = new Grocery(); grocery.name="Keep manual"; grocery.amount=2.0; grocery.unit="kg"; grocery.category="QA"; grocery.checked=true; source.dao().insertGrocery(grocery);
      String before = source.dao().recipeSnapshot().get(0).title;
      // Real v5 schema: v6 only adds this ledger. No fixture touches the app database.
      source.getWritableDatabase().execSQL("DROP TABLE backup_restores"); source.getWritableDatabase().setVersion(5); source.close(); source=new LabDatabase(c,first);
      assertEquals(6, source.getReadableDatabase().getVersion()); assertEquals(before,source.dao().recipeSnapshot().get(0).title);
      assertTrue(source.getWritableDatabase().isWriteAheadLoggingEnabled());
      assertTrue(source.dao().bookNamesSnapshot().contains("Empty fixture"));
      assertEquals(1,source.dao().mealSnapshot(null,null).size()); assertTrue(source.dao().grocerySnapshot().get(0).checked);
      JSONObject backup = BackupStore.export(c,source); BackupStore.validate(backup);
      Recipe existing = new Recipe(); existing.title="Keep existing"; existing.ingredients="water"; existing.steps="Mix"; existing.id=target.dao().insert(existing);
      assertEquals(1,BackupStore.restore(c,target,backup));
      assertEquals(0,BackupStore.restore(c,target,backup)); assertEquals(2,target.dao().recipeCount());
      assertNotNull(target.dao().find(existing.id)); assertTrue(target.dao().bookNamesSnapshot().contains("Empty fixture"));
      Recipe restored = target.dao().recipeSnapshot().stream().filter(r->r.title.equals(recipe.title)).findFirst().get();
      assertEquals(recipe.source,restored.source); assertEquals(recipe.attribution,restored.attribution); assertTrue(restored.favorite);
      assertNotEquals(photo.getAbsolutePath(),restored.image); assertTrue(new File(restored.image).isFile());
      assertEquals(1,target.dao().recipeIdsForCookbook("Second fixture").size()); assertEquals(4,target.dao().mealSnapshot(null,null).get(0).servings,0);
      assertTrue(target.dao().grocerySnapshot().get(0).checked);
      JSONObject bad = new JSONObject(backup.toString()).put("content", "{}");
      try { BackupStore.restore(c,target,bad); fail("Corrupt checksum accepted"); } catch (IOException expected) { assertTrue(expected.getMessage().contains("checksum")); }
      assertEquals(2,target.dao().recipeCount());
      // Validate transaction rollback after photo copy and recipe insert, on invalid FK mapping.
      JSONObject payload = new JSONObject(backup.getString("content"));
      payload.getJSONObject("tables").getJSONArray("cookbook_members").getJSONObject(0).put("recipeId",999999);
      bad=new JSONObject(backup.toString()).put("backupId",UUID.randomUUID().toString()).put("content",payload.toString()).put("sha256",BackupStore.digest(payload.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
      try { BackupStore.restore(c,target,bad); fail("Invalid FK accepted"); } catch (IOException expected) { assertTrue(expected.getMessage().contains("liên kết")); }
      assertEquals(2,target.dao().recipeCount());
      try(Cursor check=target.getReadableDatabase().rawQuery("PRAGMA foreign_key_check",null)){assertFalse(check.moveToFirst());}
      new File(restored.image).delete();
      source.close();source=new LabDatabase(c,first);assertEquals(recipe.image,source.dao().find(recipe.id).image);
    } finally { source.close();target.close();c.deleteDatabase(first);c.deleteDatabase(second);if(photo!=null)photo.delete(); }
  }
}
