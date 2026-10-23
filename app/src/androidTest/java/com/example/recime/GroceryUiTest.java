package com.example.recime;
import static com.example.recime.UiGestures.click;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.Espresso.onIdle;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.intent.Intents.*;
import static androidx.test.espresso.intent.matcher.IntentMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.view.*;
import android.widget.*;
import androidx.test.core.app.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.espresso.*;
import com.example.recime.activity.MainActivity;
import com.example.recime.model.*;
import com.example.recime.sql.RecipeRepository;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.hamcrest.*;
import org.junit.*;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class GroceryUiTest {
  private final Context context=ApplicationProvider.getApplicationContext();
  private RecipeRepository repo;private Recipe a,b;private String prefix;private Meal meal;
  private SharedPreferences prefs;private boolean had;private int old;
  @Before public void setup() throws Exception {
    prefs=context.getSharedPreferences("groceries",0);had=prefs.contains("sort");old=prefs.getInt("sort",0);prefs.edit().putInt("sort",0).commit();
    repo=RecipeRepository.get(context);drain();prefix="GroQA "+System.nanoTime();
    a=new Recipe();a.title=prefix+" A";a.ingredients="200 g "+prefix+" rice\n1 tbsp "+prefix+" oil";
    a.steps="Cook";a.servings=2;a.cookbook="";b=new Recipe(a);b.title=prefix+" B";
    b.ingredients="250 g "+prefix+" rice\n1 quả "+prefix+" egg";
    repo.io.submit(()->{a.id=repo.dao.insert(a);b.id=repo.dao.insert(b);}).get(30,TimeUnit.SECONDS);
  }
  @After public void cleanup() throws Exception {
    repo.io.submit(()->{
      repo.dao.delete(a);repo.dao.delete(b);
      for(Grocery g:repo.dao.grocerySnapshot())if(g.name.startsWith(prefix))repo.dao.deleteGrocery(g);
    }).get(30,TimeUnit.SECONDS);
    if(had)prefs.edit().putInt("sort",old).commit();else prefs.edit().remove("sort").commit();
  }
  private void drain() throws Exception {repo.io.submit(()->{}).get(30,TimeUnit.SECONDS);onIdle();}
  private ActivityScenario<MainActivity> open(){
    ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class);
    onView(withId(R.id.nav_grocery)).perform(new ViewAction(){
      public Matcher<View> getConstraints(){return isDisplayed();}
      public String getDescription(){return "wait for entrance before touching dock";}
      public void perform(UiController c,View v){c.loopMainThreadForAtLeast(700);}
    },click());onView(withId(R.id.board_title)).check(matches(withText("Giỏ đi chợ")));return scenario;
  }
  private Matcher<BoardRow> row(String name){return new TypeSafeMatcher<BoardRow>(){
    protected boolean matchesSafely(BoardRow r){return r.grocery!=null&&!r.grocery.planned&&r.title.equals(name);}
    public void describeTo(Description d){d.appendText("independent grocery "+name);}
  };}
  private Grocery manual(String name){return repo.dao.grocerySnapshot().stream().filter(g->g.name.equals(name)).findFirst().orElse(null);}
  private void plan() throws Exception {repo.io.submit(()->{
    meal=new Meal();meal.day="2030-01-01";meal.type="Sáng";meal.recipeId=a.id;meal.servings=4;meal.extraId=repo.dao.addMeal(meal);
  }).get(30,TimeUnit.SECONDS);onIdle();}
  private void chooseRecipe(String title){
    onData(hasToString(is(title))).inAdapterView(withId(R.id.batch_sources)).perform(click());
  }
  private void chooseBatchSources(){
    onView(withId(R.id.grocery_batch)).perform(click());
    onView(withId(R.id.batch_search)).perform(replaceText(prefix),pressImeActionButton());chooseRecipe(a.title);chooseRecipe(b.title);
    onView(withId(android.R.id.button1)).perform(click());
  }

  private java.util.List<Grocery> allGroceryRows(){
    java.util.List<Grocery> rows=new java.util.ArrayList<>(repo.dao.grocerySnapshot());
    rows.addAll(repo.dao.plannedGrocerySnapshot());return rows;
  }
  private java.util.List<String> groceryValues(java.util.List<Grocery> rows){
    java.util.List<String> values=new java.util.ArrayList<>();
    for(Grocery g:rows)values.add(g.id+"|"+g.planned+"|"+g.name+"|"+g.amount+"|"+g.unit+"|"+g.category+"|"+g.checked);
    java.util.Collections.sort(values);return values;
  }
  @Test public void manualCrudDraftAndCheckedStateSurviveRotation() throws Exception {
    java.util.List<Grocery> baseline=allGroceryRows();
    java.util.List<String> baselineValues=groceryValues(baseline);
    try(ActivityScenario<MainActivity> scenario=open()){
      onView(withId(R.id.board_action)).perform(click());
      onView(withId(R.id.grocery_name)).perform(replaceText(prefix+" manual"));
      onView(withId(R.id.grocery_amount)).perform(replaceText("0.5"));onView(withId(R.id.grocery_unit)).perform(replaceText("kg"));
      onView(withId(R.id.grocery_category)).perform(replaceText("QA group"),closeSoftKeyboard());
      scenario.recreate();onView(withId(R.id.grocery_amount)).check(matches(withText("0.5")));
      onView(withId(android.R.id.button1)).perform(click());drain();
      Grocery saved=manual(prefix+" manual");assertNotNull(saved);assertEquals(500,saved.amount,0.001);assertEquals("g",saved.unit);
      onData(row(saved.name)).inAdapterView(withId(R.id.board_list)).perform(click());
      onView(withId(R.id.grocery_name)).perform(clearText(),closeSoftKeyboard());onView(withId(android.R.id.button1)).perform(click());
      onView(withId(R.id.grocery_name)).check(matches(hasErrorText("Tên không trống; số lượng không âm, hữu hạn")));
      onView(withId(R.id.grocery_name)).perform(replaceText(prefix+" edited"));onView(withId(R.id.grocery_amount)).perform(replaceText("750"),closeSoftKeyboard());
      onView(withId(android.R.id.button1)).perform(click());drain();
      onData(row(prefix+" edited")).inAdapterView(withId(R.id.board_list)).onChildView(withId(R.id.checked)).perform(click());drain();
      scenario.recreate();onData(row(prefix+" edited")).inAdapterView(withId(R.id.board_list)).onChildView(withId(R.id.checked)).check(matches(isChecked()));
      assertEquals(750,manual(prefix+" edited").amount,0.001);assertTrue(manual(prefix+" edited").checked);
      onData(row(prefix+" edited")).inAdapterView(withId(R.id.board_list)).perform(longClick());
      onView(withId(android.R.id.button1)).perform(click());drain();assertNull(manual(prefix+" edited"));
      assertEquals("CRUD must preserve every pre-existing manual/planned row",baselineValues,groceryValues(allGroceryRows()));
      if(baseline.isEmpty())onView(withId(R.id.board_empty)).check(matches(withText(R.string.grocery_empty)));
      else{
        int bought=0;for(Grocery g:baseline)if(g.checked)bought++;
        onView(withId(R.id.board_empty)).check(matches(withText(baseline.size()+" nguyên liệu · "+bought+" đã mua")));
      }
    }
  }
  @Test public void recipeBatchScalesSelectedIngredientsAndSurvivesRotation() throws Exception {
    plan();
    try(ActivityScenario<MainActivity> scenario=open()){
      chooseBatchSources();onView(withId(R.id.batch_servings)).perform(replaceText("4"),pressImeActionButton());
      onData(anything()).inAdapterView(withId(R.id.batch_ingredients)).atPosition(1).perform(click());
      scenario.recreate();onView(withId(R.id.batch_servings)).check(matches(withText("4")));
      onData(anything()).inAdapterView(withId(R.id.batch_ingredients)).atPosition(1).check(matches(not(isChecked())));
      onView(withId(R.id.batch_entry)).perform(click());onData(is(b.title)).inRoot(androidx.test.espresso.matcher.RootMatchers.isPlatformPopup()).perform(click());
      onData(anything()).inAdapterView(withId(R.id.batch_ingredients)).atPosition(1).perform(click());
      java.util.concurrent.CountDownLatch release=new java.util.concurrent.CountDownLatch(1);
      repo.io.submit(()->{try{release.await(20,TimeUnit.SECONDS);}catch(InterruptedException failure){Thread.currentThread().interrupt();}});
      try {
        onView(withId(android.R.id.button1)).perform(click());scenario.recreate();
        onView(withId(android.R.id.button1)).check(matches(not(isEnabled())));
        release.countDown();drain();
      } finally {release.countDown();}
      onView(withId(R.id.batch_servings)).check(doesNotExist());
      assertEquals(650,manual(prefix+" rice").amount,0.001);assertNull(manual(prefix+" oil"));assertNull(manual(prefix+" egg"));
      assertEquals(2,repo.dao.find(a.id).servings,0.001);
      repo.io.submit(()->repo.dao.deleteMeal(meal)).get(30,TimeUnit.SECONDS);onIdle();
      assertEquals(650,manual(prefix+" rice").amount,0.001);
      assertTrue(repo.dao.plannedGrocerySnapshot().stream().noneMatch(g->g.name.startsWith(prefix)));
      onView(withId(R.id.board_title)).check(matches(withText("Giỏ đi chợ")));
      androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getUiAutomation().waitForIdle(1000,10000);
    }
  }
  @Test public void mealBatchStartsFromServingOverrideAndRetainsSourceSelection() throws Exception {
    plan();
    try(ActivityScenario<MainActivity> scenario=open()){
      onView(withId(R.id.grocery_batch)).perform(click());onView(withId(R.id.batch_source_mode)).perform(click());
      onData(is("Bữa ăn đã lên lịch")).inRoot(androidx.test.espresso.matcher.RootMatchers.isPlatformPopup()).perform(click());
      onView(withId(R.id.batch_search)).perform(replaceText(prefix),pressImeActionButton());
      onData(hasToString(containsString(a.title))).inAdapterView(withId(R.id.batch_sources)).perform(click());
      scenario.recreate();onData(hasToString(containsString(a.title))).inAdapterView(withId(R.id.batch_sources)).check(matches(isChecked()));
      onView(withId(android.R.id.button1)).check(matches(isEnabled())).perform(click());
      onView(withId(R.id.batch_servings)).check(matches(withText("4.0")));
      onView(withId(android.R.id.button1)).perform(click());drain();
      assertEquals(400,manual(prefix+" rice").amount,0.001);assertEquals(30,manual(prefix+" oil").amount,0.001);
      assertEquals(1,repo.dao.mealSnapshot(null,null).stream().filter(m->m.recipeId==a.id).count());
    }
  }
  @Test public void cancelSortAndShareUseActualIndependentAndPlannedRows() throws Exception {
    plan();repo.io.submit(()->{
      Grocery g=new Grocery();g.name=prefix+" own";g.amount=10.0;g.unit="g";g.category="QA group";g.checked=true;repo.dao.insertGrocery(g);
    }).get(30,TimeUnit.SECONDS);
    init();
    try(ActivityScenario<MainActivity> scenario=open()){
      int before=repo.dao.grocerySnapshot().size();chooseBatchSources();onView(withId(android.R.id.button2)).perform(click());drain();assertEquals(before,repo.dao.grocerySnapshot().size());
      onView(withId(R.id.grocery_sort)).perform(click());onData(is("Theo nguồn")).inRoot(androidx.test.espresso.matcher.RootMatchers.isPlatformPopup()).perform(click());
      scenario.recreate();onView(withId(R.id.grocery_sort)).check(matches(withSpinnerText("Theo nguồn")));
      intending(hasAction(Intent.ACTION_CHOOSER)).respondWith(new Instrumentation.ActivityResult(Activity.RESULT_CANCELED,null));
      onView(withId(R.id.board_secondary)).perform(click());intended(hasAction(Intent.ACTION_CHOOSER));
      Intent send=null;for(Intent i:getIntents())if(Intent.ACTION_CHOOSER.equals(i.getAction()))send=i.getParcelableExtra(Intent.EXTRA_INTENT);
      assertNotNull(send);String text=send.getStringExtra(Intent.EXTRA_TEXT);
      assertTrue(text.contains("[x] "+prefix+" own"));assertTrue(text.contains("Meal Planner"));assertTrue(text.contains("400 g"));
      androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getUiAutomation().waitForIdle(1000,10000);
      Bitmap image=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
      File qa=new File(context.getCacheDir(),"qa");assertTrue(qa.isDirectory()||qa.mkdirs());
      try(OutputStream out=new FileOutputStream(new File(qa,"groceries.png"))){assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,out));}finally{image.recycle();}
    }finally{release();}
  }
}
