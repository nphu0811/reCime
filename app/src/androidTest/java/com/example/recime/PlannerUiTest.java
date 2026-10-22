package com.example.recime;

import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.*;

import androidx.test.core.app.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.recime.activity.MainActivity;
import com.example.recime.sql.RecipeRepository;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class PlannerUiTest {
  @Test
  public void selectMealRecreateAndBuildChecklist() throws Exception {
    RecipeRepository repo = RecipeRepository.get(ApplicationProvider.getApplicationContext());
    repo.io.submit(() -> {}).get(20, TimeUnit.SECONDS);
    try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
      onView(withId(R.id.nav_plan)).perform(click());
      onData(anything()).inAdapterView(withId(R.id.board_list)).atPosition(0).perform(click());
      onView(withId(R.id.meal_search))
          .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
          .perform(
              replaceText("Trứng cà chua"),
              androidx.test.espresso.action.ViewActions.closeSoftKeyboard());
      onView(allOf(withId(android.R.id.text1), withText("Trứng cà chua")))
          .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
          .perform(click());
      repo.io.submit(() -> {}).get(20, TimeUnit.SECONDS);
      onIdle();
      scenario.recreate();
      onView(withText("Trứng cà chua")).check(matches(isDisplayed()));
      // The cart is now derived automatically; no old additive confirm dialog.\n      onView(withId(R.id.board_action)).perform(click());
      repo.io.submit(() -> {}).get(20, TimeUnit.SECONDS);
      onIdle();
      onView(withId(R.id.board_empty)).check(matches(withSubstring("nguyên liệu")));
    }
  }
}
