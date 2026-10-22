package com.example.recime;

import static org.junit.Assert.*;

import android.content.Context;
import androidx.room.Room;
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
    LabDatabase db = Room.databaseBuilder(context, LabDatabase.class, name).build();
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
      db = Room.databaseBuilder(context, LabDatabase.class, name).build();
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
}
