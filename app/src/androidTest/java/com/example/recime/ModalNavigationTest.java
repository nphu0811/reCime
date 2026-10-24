package com.example.recime;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.onIdle;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static com.example.recime.UiGestures.click;
import static org.junit.Assert.*;

import android.content.Context;
import android.content.Intent;
import android.view.ViewGroup;
import androidx.test.core.app.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.recime.activity.MainActivity;
import com.example.recime.fragment.*;
import com.example.recime.model.Recipe;
import com.example.recime.sql.RecipeRepository;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ModalNavigationTest {
  @Test public void nestedDraftsKeepDockHiddenAcrossTransitionsAndRestoration() throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    RecipeRepository repo = RecipeRepository.get(context);
    Recipe fixture = new Recipe(); fixture.title = "ModalQA" + System.nanoTime();
    fixture.ingredients = "100 g rice"; fixture.steps = "Cook rice.";
    repo.io.submit(() -> fixture.id = repo.dao.insert(fixture)).get(20, TimeUnit.SECONDS);
    Intent launch = new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
    try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(launch)) {
      int[] originalMargin = {0};
      scenario.onActivity(a -> {
        originalMargin[0] = ((ViewGroup.MarginLayoutParams) a.findViewById(R.id.host).getLayoutParams()).bottomMargin;
        a.open(new ImportFragment());
      }); onIdle();
      onView(withId(R.id.raw)).perform(scrollTo(), replaceText("Unsaved import text"), closeSoftKeyboard());
      onView(withId(R.id.ai_settings)).perform(scrollTo(), click());
      hidden(scenario);
      scenario.recreate(); hidden(scenario);
      pressBack();
      onView(withId(R.id.raw)).check(matches(withText("Unsaved import text")));
      hidden(scenario);
      onView(withId(R.id.manual)).perform(scrollTo(), click());
      onView(withId(R.id.edit_title)).check(matches(isDisplayed())); hidden(scenario);
      scenario.recreate(); hidden(scenario);
      pressBack(); hidden(scenario);
      onView(withId(R.id.raw)).check(matches(withText("Unsaved import text")));
      pressBack(); restored(scenario, originalMargin[0]);
      scenario.onActivity(a -> a.open(CookingFragment.create(fixture.id))); onIdle();
      hidden(scenario); scenario.recreate(); hidden(scenario);
      pressBack(); restored(scenario, originalMargin[0]);
    } finally { repo.io.submit(() -> repo.dao.delete(fixture)).get(20, TimeUnit.SECONDS); }
  }
  private void hidden(ActivityScenario<MainActivity> scenario) {
    onView(withId(R.id.bottom)).check(matches(withEffectiveVisibility(Visibility.GONE)));
    scenario.onActivity(a -> assertEquals(0, ((ViewGroup.MarginLayoutParams) a.findViewById(R.id.host).getLayoutParams()).bottomMargin));
  }
  private void restored(ActivityScenario<MainActivity> scenario, int margin) {
    scenario.onActivity(a -> assertEquals("Root must have no pending modal entry; current="
        + a.getSupportFragmentManager().findFragmentById(R.id.host).getClass().getSimpleName(),
        0, a.getSupportFragmentManager().getBackStackEntryCount()));
    onView(withId(R.id.bottom)).check(matches(isDisplayed()));
    scenario.onActivity(a -> assertEquals(margin, ((ViewGroup.MarginLayoutParams) a.findViewById(R.id.host).getLayoutParams()).bottomMargin));
  }
}
