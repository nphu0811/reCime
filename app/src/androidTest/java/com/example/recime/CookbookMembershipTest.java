package com.example.recime;

import static org.junit.Assert.*;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.recime.model.Grocery;
import com.example.recime.model.Meal;
import com.example.recime.model.Recipe;
import com.example.recime.sql.LabDatabase;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Membership changes preserve recipes, unrelated data and an explicitly empty selection. */
@RunWith(AndroidJUnit4.class)
public class CookbookMembershipTest {
  @Test
  public void removePrimarySecondaryAndAllMembershipsSurviveEditsAndReopen() {
    Context context = ApplicationProvider.getApplicationContext();
    String name = "test-memberships-" + System.nanoTime() + ".db";
    LabDatabase db = new LabDatabase(context, name);
    try {
      Recipe recipe = new Recipe();
      recipe.title = "Món thử membership";
      recipe.ingredients = "200 g gạo";
      recipe.steps = "Nấu";
      recipe.cookbook = "Bữa sáng";
      recipe.id = db.dao().insert(recipe);
      db.dao().createCookbook("Bữa tối");
      db.dao().createCookbook("Cuối tuần");
      db.dao().replaceRecipeCookbooks(recipe.id, Arrays.asList("Bữa sáng", "Bữa tối", "Cuối tuần"));
      assertEquals(3, db.dao().cookbookNamesForRecipe(recipe.id).size());

      Meal meal = new Meal();
      meal.day = "2026-10-23";
      meal.type = "Sáng";
      meal.slot = meal.day + "|" + meal.type;
      meal.recipeId = recipe.id;
      db.dao().putMeal(meal);
      Grocery manual = new Grocery();
      manual.name = "Sữa";
      manual.amount = 100.0;
      manual.unit = "ml";
      db.dao().insertGrocery(manual);

      // Remove the primary cookbook, then edit the recipe and remove a secondary one.
      db.dao().replaceRecipeCookbooks(recipe.id, Arrays.asList("Bữa tối", "Cuối tuần"));
      Recipe edited = db.dao().find(recipe.id);
      assertEquals("Bữa tối", edited.cookbook);
      edited.favorite = true;
      edited.title = "Đã sửa";
      db.dao().update(edited);
      assertFalse(db.dao().recipeIdsForCookbook("Bữa sáng").contains(recipe.id));
      db.dao().replaceRecipeCookbooks(recipe.id, Collections.singletonList("Bữa tối"));
      assertFalse(db.dao().recipeIdsForCookbook("Cuối tuần").contains(recipe.id));

      db.dao().replaceRecipeCookbooks(recipe.id, Collections.emptyList());
      edited = db.dao().find(recipe.id);
      assertEquals("", edited.cookbook);
      edited.steps = "Hướng dẫn đã sửa";
      db.dao().update(edited);
      assertTrue(db.dao().cookbookNamesForRecipe(recipe.id).isEmpty());
      db.close();
      db = new LabDatabase(context, name);
      assertTrue(db.dao().cookbookNamesForRecipe(recipe.id).isEmpty());
      Recipe saved = db.dao().find(recipe.id);
      assertEquals("Đã sửa", saved.title);
      assertEquals("Hướng dẫn đã sửa", saved.steps);
      assertTrue(saved.favorite);
      assertEquals(1, db.dao().mealSnapshot(meal.day, meal.day).size());
      assertEquals(1, db.dao().grocerySnapshot().size());
      assertEquals(200.0, db.dao().plannedGrocerySnapshot().get(0).amount, 0.001);
    } finally {
      db.close();
      context.deleteDatabase(name);
    }
  }

  @Test
  public void invalidSelectionKeepsEntireOldSelectionAndRecipe() {
    Context context = ApplicationProvider.getApplicationContext();
    LabDatabase db = new LabDatabase(context, null);
    try {
      Recipe recipe = new Recipe();
      recipe.title = "Giữ nguyên";
      recipe.cookbook = "Gốc";
      recipe.id = db.dao().insert(recipe);
      db.dao().createCookbook("Mới");
      try {
        db.dao().replaceRecipeCookbooks(recipe.id, Arrays.asList("Mới", "Không tồn tại"));
        fail("An invalid cookbook must reject the whole selection");
      } catch (IllegalArgumentException expected) {
        assertEquals(Collections.singletonList("Gốc"), db.dao().cookbookNamesForRecipe(recipe.id));
        assertEquals("Gốc", db.dao().find(recipe.id).cookbook);
        assertEquals("Giữ nguyên", db.dao().find(recipe.id).title);
      }
      assertTrue(db.dao().recipeIdsForCookbook("Mới").isEmpty());
    } finally {
      db.close();
    }
  }
}
