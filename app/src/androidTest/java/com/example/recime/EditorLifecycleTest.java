package com.example.recime;
import static com.example.recime.UiGestures.click;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.onIdle;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.*;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.recime.activity.MainActivity;
import com.example.recime.fragment.EditorFragment;
import com.example.recime.model.Recipe;
import com.example.recime.sql.RecipeRepository;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class EditorLifecycleTest {
  private RecipeRepository repository() throws Exception {
    RecipeRepository repo = RecipeRepository.get(ApplicationProvider.getApplicationContext());
    drain(repo);
    return repo;
  }

  private void drain(RecipeRepository repo) throws Exception {
    repo.io.submit(() -> {}).get(30, TimeUnit.SECONDS);
    onIdle();
  }

  @Test public void unsavedDraftSurvivesLibraryRefreshAndRecreation() throws Exception {
    RecipeRepository repo = repository();
    Recipe fixture = new Recipe();
    fixture.title = "Editor lifecycle " + System.nanoTime();
    fixture.ingredients = "200 g gạo"; fixture.steps = "Nấu cơm.";
    repo.io.submit(() -> fixture.id = repo.dao.insert(fixture)).get(30, TimeUnit.SECONDS);
    try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
      scenario.onActivity(a -> a.open(EditorFragment.create(fixture.id, null, "", "")));
      onView(withId(R.id.edit_title)).perform(scrollTo(), replaceText("Draft title"), closeSoftKeyboard());
      repo.io.submit(() -> repo.dao.refresh()).get(30, TimeUnit.SECONDS);
      onIdle();
      onView(withId(R.id.edit_title)).check(matches(withText("Draft title")));
      scenario.recreate();
      onView(withId(R.id.edit_title)).perform(scrollTo()).check(matches(withText("Draft title")));
      assertEquals(fixture.title, repo.dao.find(fixture.id).title);
    } finally { repo.io.submit(() -> repo.dao.delete(fixture)).get(30, TimeUnit.SECONDS); }
  }

  @Test public void invalidNutritionDoesNotMutateObservedRecipeAndCanRetry() throws Exception {
    RecipeRepository repo = repository();
    Recipe fixture = new Recipe();
    fixture.title = "Editor validation " + System.nanoTime();
    fixture.ingredients = "200 g gạo"; fixture.steps = "Nấu cơm.";
    fixture.favorite = true; fixture.servingsKnown = false;
    fixture.catalogKey = "editor-regression-" + System.nanoTime();
    fixture.attribution = "Test attribution"; fixture.imageAttribution = "Test image credit";
    repo.io.submit(() -> fixture.id = repo.dao.insert(fixture)).get(30, TimeUnit.SECONDS);
    try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
      final Recipe[] observed = {null};
      scenario.onActivity(a -> {
        for (Recipe r : repo.dao.recipes().getValue()) if (r.id == fixture.id) observed[0] = r;
        a.open(EditorFragment.create(fixture.id, null, "", ""));
      });
      assertNotNull(observed[0]);
      onView(withId(R.id.edit_title)).perform(scrollTo(), replaceText("Valid retry"), closeSoftKeyboard());
      onView(withId(R.id.nutrition_toggle)).perform(scrollTo(), click());
      onView(withId(R.id.edit_kcal)).perform(scrollTo(), replaceText("120"), closeSoftKeyboard());
      onView(withId(R.id.save)).perform(click());
      onView(withId(R.id.edit_error)).check(matches(withSubstring("nguồn dinh dưỡng")));
      assertEquals(fixture.title, observed[0].title);
      assertEquals(fixture.title, repo.dao.find(fixture.id).title);
      onView(withId(R.id.edit_nutrition_source)).perform(scrollTo(), replaceText("Test fixture source"), closeSoftKeyboard());
      onView(withId(R.id.save)).perform(click());
      drain(repo);
      assertEquals("Valid retry", repo.dao.find(fixture.id).title);
      assertEquals(120.0, repo.dao.find(fixture.id).calories, 0.001);
      Recipe saved = repo.dao.find(fixture.id);
      assertTrue(saved.favorite);
      assertFalse(saved.servingsKnown);
      assertEquals(fixture.catalogKey, saved.catalogKey);
      assertEquals(fixture.attribution, saved.attribution);
      assertEquals(fixture.imageAttribution, saved.imageAttribution);
    } finally { repo.io.submit(() -> repo.dao.delete(fixture)).get(30, TimeUnit.SECONDS); }
  }

  @Test public void pendingNewRecipeSaveSurvivesRecreationWithoutDuplicate() throws Exception {
    RecipeRepository repo = repository();
    String title = "Editor queued " + System.nanoTime();
    CountDownLatch release = new CountDownLatch(1);
    try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
      scenario.onActivity(a -> a.open(EditorFragment.create(0, null, "", "")));
      onView(withId(R.id.edit_title)).perform(scrollTo(), replaceText(title), closeSoftKeyboard());
      onView(withId(R.id.edit_ingredients)).perform(scrollTo(), replaceText("200 g gạo"), closeSoftKeyboard());
      onView(withId(R.id.edit_steps)).perform(scrollTo(), replaceText("Nấu cơm."), closeSoftKeyboard());
      repo.io.execute(() -> {
        try { release.await(30, TimeUnit.SECONDS); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
      });
      onView(withId(R.id.save)).perform(click());
      scenario.recreate();
      onView(withId(R.id.save)).check(matches(not(isEnabled())));
      release.countDown();
      drain(repo);
      onView(withId(R.id.save)).check(doesNotExist());
      long count = repo.dao.recipeSnapshot().stream().filter(r -> title.equals(r.title)).count();
      assertEquals(1, count);
    } finally {
      release.countDown();
      repo.io.submit(() -> {
        for (Recipe r : repo.dao.recipeSnapshot()) if (title.equals(r.title)) repo.dao.delete(r);
      }).get(30, TimeUnit.SECONDS);
    }
  }
}
