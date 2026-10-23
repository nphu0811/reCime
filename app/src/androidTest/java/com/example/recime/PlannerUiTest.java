package com.example.recime;

import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import androidx.test.core.app.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.recime.activity.MainActivity;
import com.example.recime.fragment.DetailFragment;
import com.example.recime.model.BoardRow;
import com.example.recime.model.Grocery;
import com.example.recime.model.Meal;
import com.example.recime.model.Recipe;
import com.example.recime.sql.LabDatabase;
import com.example.recime.sql.RecipeRepository;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class PlannerUiTest {
  @Test
  public void selectMealRecreateAndBuildChecklist() throws Exception {
    RecipeRepository repo = RecipeRepository.get(ApplicationProvider.getApplicationContext());
    repo.io.submit(() -> {}).get(20, TimeUnit.SECONDS);
    Recipe fixture = fixture("Planner checklist");
    repo.io.submit(() -> fixture.id = repo.dao.insert(fixture)).get(20, TimeUnit.SECONDS);
    try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
      onView(withId(R.id.nav_plan)).perform(new androidx.test.espresso.ViewAction(){
        public org.hamcrest.Matcher<android.view.View> getConstraints(){return isDisplayed();}
        public String getDescription(){return "wait for initial dock animation before touching Planner";}
        public void perform(androidx.test.espresso.UiController controller,android.view.View view){controller.loopMainThreadForAtLeast(700);}
      },click());
      Calendar monday = Calendar.getInstance();
      monday.add(Calendar.DAY_OF_MONTH, -((monday.get(Calendar.DAY_OF_WEEK) + 5) % 7));
      String day = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(monday.getTime());
      // Target the add row, so an existing user's first meal is never replaced.
      onData(new TypeSafeMatcher<BoardRow>() {
        @Override public boolean matchesSafely(BoardRow row) {
          return row.meal == null && day.equals(row.day) && "Sáng".equals(row.type);
        }
        @Override public void describeTo(Description description) {
          description.appendText("Monday breakfast add-meal row");
        }
      }).inAdapterView(withId(R.id.board_list)).perform(click());
      onView(withId(R.id.meal_search))
          .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
          .perform(
              replaceText(fixture.title),
              androidx.test.espresso.action.ViewActions.closeSoftKeyboard());
      onView(allOf(withId(android.R.id.text1), withText(fixture.title)))
          .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
          .perform(click());
      repo.io.submit(() -> {}).get(20, TimeUnit.SECONDS);
      onIdle();
      scenario.recreate();
      onData(hasTitle(fixture.title)).inAdapterView(withId(R.id.board_list))
          .check(matches(isDisplayed()));
      // The cart is now derived automatically; no old additive confirm dialog.
      onView(withId(R.id.board_action)).perform(click());
      repo.io.submit(() -> {}).get(20, TimeUnit.SECONDS);
      onIdle();
      onView(withId(R.id.board_empty)).check(matches(withSubstring("nguyên liệu")));
      assertEquals(200.0, plannedIngredient(repo, fixture).amount, 0.001);
    } finally {
      repo.io.submit(() -> repo.dao.delete(fixture)).get(20, TimeUnit.SECONDS);
    }
  }

  @Test
  public void detailSelectedPortionsSurviveRecreateAndPersistInFirstPlanEntry() throws Exception {
    RecipeRepository repo = RecipeRepository.get(ApplicationProvider.getApplicationContext());
    repo.io.submit(() -> {}).get(20, TimeUnit.SECONDS);
    Recipe fixture = fixture("Planner portions");
    repo.io.submit(() -> fixture.id = repo.dao.insert(fixture)).get(20, TimeUnit.SECONDS);
    try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
      scenario.onActivity(activity -> activity.open(DetailFragment.create(fixture.id)));
      onView(withId(R.id.portions)).perform(scrollTo()).check(matches(withText("2 khẩu phần")));
      onView(withId(R.id.plus)).perform(click(), click());
      scenario.recreate();
      onView(withId(R.id.portions)).perform(scrollTo()).check(matches(withText("4 khẩu phần")));
      onView(withId(R.id.to_plan)).perform(scrollTo(), click());
      onView(withId(android.R.id.button1))
          .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click());
      onView(withText("Sáng"))
          .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click());
      repo.io.submit(() -> {}).get(20, TimeUnit.SECONDS);
      onIdle();
      assertEquals(400.0, plannedIngredient(repo, fixture).amount, 0.001);

      // A separate helper opens the persisted file; assertions do not rely on LiveData.
      LabDatabase reopened = new LabDatabase(ApplicationProvider.getApplicationContext());
      try {
        int count = 0;
        for (Meal meal : reopened.dao().mealSnapshot(null, null)) {
          if (meal.recipeId != fixture.id) continue;
          count++;
          assertTrue(meal.extraId > 0);
          assertEquals(4.0, meal.servings, 0.001);
        }
        assertEquals(1, count);
        Grocery persisted = null;
        for (Grocery grocery : reopened.dao().plannedGrocerySnapshot())
          if (grocery.name.equals(fixture.title)) persisted = grocery;
        assertNotNull(persisted);
        assertEquals(400.0, persisted.amount, 0.001);
      } finally {
        reopened.close();
      }
    } finally {
      repo.io.submit(() -> repo.dao.delete(fixture)).get(20, TimeUnit.SECONDS);
    }
  }

  private static Recipe fixture(String prefix) {
    Recipe recipe = new Recipe();
    recipe.title = prefix + " " + System.nanoTime();
    recipe.ingredients = "200 g " + recipe.title;
    recipe.steps = "Nấu nguyên liệu.";
    recipe.servings = 2;
    recipe.servingsKnown = true;
    return recipe;
  }

  private static Grocery plannedIngredient(RecipeRepository repo, Recipe recipe) {
    for (Grocery grocery : repo.dao.plannedGrocerySnapshot())
      if (grocery.name.equals(recipe.title)) return grocery;
    throw new AssertionError("Missing derived grocery for " + recipe.title);
  }

  private static TypeSafeMatcher<BoardRow> hasTitle(String title) {
    return new TypeSafeMatcher<BoardRow>() {
      @Override public boolean matchesSafely(BoardRow row) { return title.equals(row.title); }
      @Override public void describeTo(Description description) { description.appendText(title); }
    };
  }
}
