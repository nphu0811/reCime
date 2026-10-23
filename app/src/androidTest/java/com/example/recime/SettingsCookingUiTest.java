package com.example.recime;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.onIdle;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static com.example.recime.UiGestures.click;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import android.content.Context;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import androidx.test.core.app.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.recime.activity.MainActivity;
import com.example.recime.fragment.*;
import com.example.recime.helper.CookingTimer;
import com.example.recime.model.Recipe;
import com.example.recime.sql.RecipeRepository;
import java.util.concurrent.TimeUnit;
import org.junit.*;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SettingsCookingUiTest {
  private Context context;
  private RecipeRepository repo;
  private Recipe recipe;
  @Before public void setup() throws Exception {
    context=ApplicationProvider.getApplicationContext();repo=RecipeRepository.get(context);repo.io.submit(()->{}).get(30,TimeUnit.SECONDS);
    recipe=new Recipe();recipe.title="CookingQA"+System.nanoTime();recipe.ingredients="100 g rice\nwater as needed";recipe.steps="Wash rice\nCook rice\nServe";recipe.servings=2;
    repo.io.submit(()->recipe.id=repo.dao.insert(recipe)).get(30,TimeUnit.SECONDS);
  }
  @After public void cleanup() throws Exception { repo.io.submit(()->repo.dao.delete(recipe)).get(30,TimeUnit.SECONDS);CookingTimer.cancel(context); }
  @Test public void settingsKeepEmailButNeverRestorePasswordAndWelcomeReturns() throws Exception {
    try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
      onView(withId(R.id.open_settings)).perform(click());
      onView(withId(R.id.settings_email)).perform(scrollTo(),replaceText("draft@example.org"),closeSoftKeyboard());
      onView(withId(R.id.settings_password)).perform(scrollTo(),replaceText("test-only-draft-password"),closeSoftKeyboard());
      scenario.recreate();onIdle();
      onView(withId(R.id.settings_email)).check(matches(withText("draft@example.org")));
      onView(withId(R.id.settings_password)).check(matches(withText("")));
      onView(withId(R.id.settings_welcome)).perform(scrollTo(),click());
      onView(withText("Bắt đầu / Tiếp tục Guest")).perform(scrollTo(),click());
      onView(withId(R.id.settings_status)).perform(scrollTo()).check(matches(isDisplayed()));
      pressBack();onView(withId(R.id.heading)).check(matches(withText("Recipes")));
    }
  }
  @Test public void cookingScalesChecklistRetainsStepRotationAndBackgroundTimer() throws Exception {
    try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
      scenario.onActivity(a->a.open(CookingFragment.create(recipe.id,4)));
      onView(withId(R.id.cook_servings)).check(matches(withText(containsString("4 khẩu phần"))));
      onView(withText("200 g rice")).perform(click());
      onView(withId(R.id.cook_next)).perform(click());
      onView(withId(R.id.cook_step)).check(matches(withText("Cook rice")));
      CookingTimer.start(context,recipe.id,recipe.title,1);
      scenario.recreate();onIdle();
      onView(withId(R.id.cook_step)).check(matches(withText("Cook rice")));
      onView(withText("200 g rice")).check(matches(isChecked()));
      assertTrue(CookingTimer.remaining(context)>0);
      scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED);
      assertTrue(CookingTimer.remaining(context)>0);
      scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED);onIdle();
      onView(withId(R.id.cook_timer_cancel)).perform(click());assertEquals(-1,CookingTimer.remaining(context));
      onView(withId(R.id.cook_previous)).perform(click());
      onView(withId(R.id.cook_step)).check(matches(withText("Wash rice")));
    }
  }
  @Test public void discoverCopiesToPersonalWithoutDroppingLicenseOrOriginal() throws Exception {
    repo.io.submit(()->{Recipe update=new Recipe(recipe);update.catalogKey="catalogQA"+System.nanoTime();update.source="https://example.org/fixture";update.attribution="QA test CC0";update.cookbook="QA catalog";repo.dao.update(update);recipe=update;}).get(30,TimeUnit.SECONDS);
    try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
      scenario.onActivity(a->a.open(DetailFragment.create(recipe.id)));
      onView(withId(R.id.save_catalog)).perform(scrollTo(),click());
      repo.io.submit(()->{}).get(30,TimeUnit.SECONDS);onIdle();
      Recipe copy=repo.dao.recipeSnapshot().stream().filter(r->r.title.equals(recipe.title)&&r.catalogKey==null).findFirst().orElse(null);
      assertNotNull(copy);assertNotEquals(recipe.id,copy.id);assertEquals(recipe.source,copy.source);assertEquals(recipe.attribution,copy.attribution);assertNotNull(repo.dao.find(recipe.id));
      repo.io.submit(()->repo.dao.delete(copy)).get(30,TimeUnit.SECONDS);
    }
  }
}
