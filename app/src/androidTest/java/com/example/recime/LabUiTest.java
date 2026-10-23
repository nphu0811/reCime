package com.example.recime;
import static com.example.recime.UiGestures.click;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.junit.Assert.*;

import android.content.*;
import androidx.test.core.app.*;
import androidx.test.espresso.Espresso;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.recime.activity.MainActivity;
import com.example.recime.model.Recipe;
import com.example.recime.sql.RecipeRepository;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class LabUiTest {
  @Test
  public void manualImportSaveSearchDetailScaleAndGroceryPersist() throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    RecipeRepository repo = RecipeRepository.get(context);
    String title = "UITest " + System.nanoTime();
    Intent intent =
        new Intent(context, MainActivity.class)
            .setAction(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(
                Intent.EXTRA_TEXT,
                title + "\nNguyên liệu\n200 g đậu phụ\n1 muỗng canh dầu\nCách làm\nÁp chảo đậu.");
    try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(intent)) {
      onView(withId(R.id.review)).perform(scrollTo(), click());
      onView(withId(R.id.edit_title)).check(matches(withText(title)));
      onView(withId(R.id.save)).perform(click());
      repo.io.submit(() -> {}).get(20, TimeUnit.SECONDS);
      Espresso.onIdle();
      onView(withId(R.id.nav_recipes)).perform(click());
      onView(withId(R.id.search)).perform(replaceText(title), closeSoftKeyboard());
      onView(org.hamcrest.Matchers.allOf(withId(R.id.title), withText(title))).perform(click());
      onView(withId(R.id.plus)).perform(scrollTo(), click());
      onView(withId(R.id.detail_ingredients)).check(matches(withSubstring("300 g")));
      onView(withId(R.id.to_grocery)).perform(scrollTo(), click());
      repo.io.submit(() -> {}).get(20, TimeUnit.SECONDS);
      Espresso.onIdle();
      onView(withId(R.id.nav_grocery)).perform(click());
      onView(withId(R.id.board_title)).check(matches(withText("Giỏ đi chợ")));
      scenario.recreate();
      Espresso.onIdle();
      onView(withId(R.id.board_title)).check(matches(withText("Giỏ đi chợ")));
      Recipe saved =
          repo.io
              .submit(
                  () -> {
                    for (Recipe r : repo.dao.recipeSnapshot()) if (r.title.equals(title)) return r;
                    return null;
                  })
              .get(20, TimeUnit.SECONDS);
      assertNotNull(saved);
      assertEquals(2, saved.servings, 0.0001);
    } finally {
      repo.io
          .submit(
              () -> {
                for (Recipe r : repo.dao.recipeSnapshot())
                  if (r.title.equals(title)) repo.dao.delete(r);
              })
          .get(20, TimeUnit.SECONDS);
    }
  }

  @Test
  public void tabsHaveRealContentAndInvalidUrlHasRecoverableError() throws Exception {
    try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
      onView(withId(R.id.nav_plan)).perform(click());
      onView(withId(R.id.board_title)).check(matches(withText("Meal Plan")));
      onView(withId(R.id.next_week)).perform(click());
      onView(withId(R.id.prev_week)).perform(click());
      onView(withId(R.id.nav_discover)).perform(click());
      onView(withId(R.id.heading)).check(matches(withText("Discover")));
      onView(withId(R.id.add)).perform(click());
      onView(withText("Nhập link, ảnh, văn bản / AI")).perform(click());
      onView(withId(R.id.url)).perform(scrollTo(), replaceText("invalid-url"), closeSoftKeyboard());
      onView(withId(R.id.fetch)).perform(scrollTo(), click());
      RecipeRepository.get(ApplicationProvider.getApplicationContext())
          .io
          .submit(() -> {})
          .get(20, TimeUnit.SECONDS);
      Espresso.onIdle();
      onView(withId(R.id.status)).check(matches(withSubstring("Không nhập được")));
      onView(withId(R.id.fetch)).check(matches(isEnabled()));
    }
  }
}
