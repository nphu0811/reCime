package com.example.recime;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.junit.Assert.*;
import static com.example.recime.UiGestures.click;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.recime.activity.MainActivity;
import com.example.recime.model.Recipe;
import com.example.recime.sql.RecipeRepository;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ImportDuplicateUiTest {
  @Test public void duplicatePromptCanOpenExistingWithoutMakingAnotherCopy() throws Exception {
    RecipeRepository repository = RecipeRepository.get(ApplicationProvider.getApplicationContext());
    repository.io.submit(() -> {}).get(30, TimeUnit.SECONDS);
    Recipe existing = new Recipe();
    existing.title = "QA import existing " + System.nanoTime();
    existing.ingredients = "200 g rice";
    existing.steps = "Cook in water.";
    existing.source = "https://recipes.example/import/" + System.nanoTime();
    repository.io.submit(() -> existing.id = repository.dao.insert(existing)).get(30, TimeUnit.SECONDS);

    try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
      // Exercise the actual Home → + → Import navigation. Launching a fragment
      // immediately inside onActivity races the activity's window focus on API 37.
      onView(withId(R.id.add)).perform(click());
      onView(withText("Nhập link, ảnh, văn bản / AI")).perform(click());
      onView(withId(R.id.url)).check(matches(isDisplayed()));
      onView(withId(R.id.url)).perform(scrollTo(), replaceText(existing.source),
          closeSoftKeyboard());
      onView(withId(R.id.raw)).perform(scrollTo(), replaceText(
          "New title\nIngredients\n100 g tofu\nInstructions\nSteam tofu."),
          closeSoftKeyboard());
      onView(withId(R.id.review)).perform(scrollTo(), click());
      repository.io.submit(() -> {}).get(30, TimeUnit.SECONDS);
      onView(withText("Có thể bạn đã lưu công thức này")).check(matches(isDisplayed()));
      onView(withText("Mở bản đã lưu")).perform(click());
      onView(withId(R.id.detail_title)).check(matches(withText(existing.title)));
      int matches = repository.io.submit(() -> {
        int count = 0;
        for (Recipe recipe : repository.dao.recipeSnapshot())
          if (recipe.source.equals(existing.source) && recipe.catalogKey == null) count++;
        return count;
      }).get(30, TimeUnit.SECONDS);
      assertEquals("Opening the existing recipe must never save a duplicate", 1, matches);
    } finally {
      repository.io.submit(() -> repository.dao.delete(existing)).get(30, TimeUnit.SECONDS);
    }
  }

  @Test public void duplicatePromptAllowsEditingANewCopyWithoutSavingAutomatically()
      throws Exception {
    RecipeRepository repository = RecipeRepository.get(ApplicationProvider.getApplicationContext());
    repository.io.submit(() -> {}).get(30, TimeUnit.SECONDS);
    Recipe existing = new Recipe();
    existing.title = "QA recipe import";
    existing.ingredients = "200 g rice";
    existing.steps = "Cook in water.";
    existing.source = "https://recipes.example/import/" + System.nanoTime();
    repository.io.submit(() -> existing.id = repository.dao.insert(existing)).get(30, TimeUnit.SECONDS);

    try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
      // Exercise the actual Home → + → Import navigation. Launching a fragment
      // immediately inside onActivity races the activity's window focus on API 37.
      onView(withId(R.id.add)).perform(click());
      onView(withText("Nhập link, ảnh, văn bản / AI")).perform(click());
      onView(withId(R.id.url)).check(matches(isDisplayed()));
      onView(withId(R.id.url)).perform(scrollTo(), replaceText(existing.source),
          closeSoftKeyboard());
      onView(withId(R.id.raw)).perform(scrollTo(), replaceText(
          "New title\nIngredients\n100 g tofu\nInstructions\nSteam tofu."),
          closeSoftKeyboard());
      onView(withId(R.id.review)).perform(scrollTo(), click());
      repository.io.submit(() -> {}).get(30, TimeUnit.SECONDS);
      onView(withText("Nhập bản sao")).perform(click());
      onView(withId(R.id.edit_title)).check(matches(withText("New title")));
      onView(withId(R.id.edit_ingredients)).check(matches(withText("100 g tofu")));
      assertNotNull(repository.io.submit(() -> repository.dao.find(existing.id))
          .get(30, TimeUnit.SECONDS));
      int matches = repository.io.submit(() -> {
        int count = 0;
        for (Recipe recipe : repository.dao.recipeSnapshot())
          if (existing.source.equals(recipe.source) && recipe.catalogKey == null) count++;
        return count;
      }).get(30, TimeUnit.SECONDS);
      assertEquals("Editor review does not implicitly commit a duplicate", 1, matches);
    } finally {
      repository.io.submit(() -> repository.dao.delete(existing)).get(30, TimeUnit.SECONDS);
    }
  }
}
