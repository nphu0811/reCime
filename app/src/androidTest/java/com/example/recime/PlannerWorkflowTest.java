package com.example.recime;
import static com.example.recime.UiGestures.click;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.Espresso.onIdle;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import androidx.test.core.app.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.espresso.*;
import com.example.recime.activity.MainActivity;
import com.example.recime.fragment.DetailFragment;
import com.example.recime.helper.PlannerWeek;
import com.example.recime.model.*;
import com.example.recime.sql.RecipeRepository;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.hamcrest.*;
import org.junit.*;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class PlannerWorkflowTest {
  private final Context context=ApplicationProvider.getApplicationContext();
  private RecipeRepository repo;private Recipe recipe;private Meal source;
  private SharedPreferences prefs;private boolean had;private int old;
  private Calendar start;
  @Before public void setup() throws Exception {
    prefs=context.getSharedPreferences("planner",0);had=prefs.contains("first_day");old=prefs.getInt("first_day",2);
    prefs.edit().putInt("first_day",Calendar.MONDAY).commit();
    repo=RecipeRepository.get(context);drain();
    recipe=new Recipe();recipe.title="Planner workflow "+System.nanoTime();recipe.cookbook="";
    recipe.ingredients="200 g "+recipe.title;recipe.steps="Nấu";recipe.servings=2;
    start=PlannerWeek.start(Calendar.getInstance(),Calendar.MONDAY);
    repo.io.submit(()->{
      recipe.id=repo.dao.insert(recipe);
      source=new Meal();source.day=PlannerWeek.day(start);source.type="Sáng";source.recipeId=recipe.id;source.servings=4;
      source.slot=source.day+"|"+source.type;source.extraId=repo.dao.addMeal(source);
      Meal other=new Meal(source);other.extraId=0;other.type="Tối";other.servings=2;repo.dao.addMeal(other);
    }).get(30,TimeUnit.SECONDS);
  }
  @After public void cleanup() throws Exception {
    repo.io.submit(()->repo.dao.delete(recipe)).get(30,TimeUnit.SECONDS);
    if(had)prefs.edit().putInt("first_day",old).commit();else prefs.edit().remove("first_day").commit();
  }
  private void drain() throws Exception {repo.io.submit(()->{}).get(30,TimeUnit.SECONDS);onIdle();}
  private ActivityScenario<MainActivity> open(){
    ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class);
    onView(withId(R.id.nav_plan)).perform(click());
    onView(withId(R.id.board_list)).check(matches(isDisplayed()));return s;
  }
  private Matcher<BoardRow> sourceRow(){return new TypeSafeMatcher<BoardRow>(){
    @Override protected boolean matchesSafely(BoardRow row){return row.meal!=null&&row.meal.extraId==source.extraId;}
    @Override public void describeTo(Description d){d.appendText("exact planned occurrence "+source.extraId);}
  };}
  private void option(String label){onData(sourceRow()).inAdapterView(withId(R.id.board_list)).perform(click());onView(withText(label)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click());}
  private Meal saved(){return repo.dao.mealSnapshot(null,null).stream().filter(m->m.extraId==source.extraId).findFirst().get();}
  private double grocery(){return repo.dao.plannedGrocerySnapshot().stream().filter(g->g.name.equals(recipe.title)).findFirst().get().amount;}
  private String label(ActivityScenario<MainActivity> s){String[] text={""};s.onActivity(a->text[0]=((TextView)a.findViewById(R.id.week_label)).getText().toString());return text[0];}

  @Test public void weekStartPersistsAndNextWeekSurvivesRotationAndDetailReturn() throws Exception {
    try(ActivityScenario<MainActivity> s=open()){
      onView(withId(R.id.plan_week_start)).perform(click());onView(withText("Sunday")).perform(click());
      assertEquals(Calendar.SUNDAY,prefs.getInt("first_day",0));
      s.onActivity(a->{
        ListView list=a.findViewById(R.id.board_list);int headers=0,adds=0;
        for(int n=0;n<list.getAdapter().getCount();n++){
          BoardRow row=(BoardRow)list.getAdapter().getItem(n);
          if(row.dayHeader)headers++;else if(row.meal==null)adds++;
        }
        assertEquals(7,headers);assertEquals(28,adds);
      });
      onView(withId(R.id.next_week)).perform(click());String expected=label(s);
      s.recreate();assertEquals(expected,label(s));
      s.onActivity(a->a.open(DetailFragment.create(recipe.id)));
      onView(withId(R.id.back)).perform(scrollTo(),click());assertEquals(expected,label(s));
      onView(withId(R.id.prev_week)).perform(click());
      Calendar sunday=PlannerWeek.start(Calendar.getInstance(),Calendar.SUNDAY);
      assertTrue(label(s).startsWith(PlannerWeek.day(sunday)));
      assertEquals(2,repo.dao.mealSnapshot(null,null).stream().filter(m->m.recipeId==recipe.id).count());
    }
    try(ActivityScenario<MainActivity> reopened=open()){
      assertTrue(label(reopened).startsWith(PlannerWeek.day(PlannerWeek.start(Calendar.getInstance(),Calendar.SUNDAY))));
    }
  }
  @Test public void portionsAndMoveDialogsRetainDraftThenRemoveRecomputesOnlyPlannedItems() throws Exception {
    try(ActivityScenario<MainActivity> s=open()){
      option("Khẩu phần");onView(withId(R.id.plan_item_servings)).perform(replaceText("3"),closeSoftKeyboard());
      s.recreate();onView(withId(R.id.plan_item_servings)).check(matches(withText("3")));
      onView(withId(android.R.id.button1)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click());drain();
      assertEquals(3,saved().servings,0.001);assertEquals(500,grocery(),0.001);
      option("Chuyển món");Calendar target=(Calendar)start.clone();target.add(Calendar.DAY_OF_MONTH,8);
      onView(withId(R.id.plan_move_date)).perform(new ViewAction(){
        public Matcher<View> getConstraints(){return isAssignableFrom(DatePicker.class);}
        public String getDescription(){return "choose destination calendar date";}
        public void perform(UiController c,View view){((DatePicker)view).updateDate(target.get(Calendar.YEAR),target.get(Calendar.MONTH),target.get(Calendar.DAY_OF_MONTH));}
      });
      onView(withId(R.id.plan_move_type)).perform(click());onData(is("Snack")).inRoot(androidx.test.espresso.matcher.RootMatchers.isPlatformPopup()).perform(click());
      s.recreate();onView(withId(R.id.plan_move_type)).check(matches(withSpinnerText("Snack")));
      onView(withId(R.id.plan_move_date)).check(matches(new TypeSafeMatcher<View>(){
        protected boolean matchesSafely(View v){DatePicker p=(DatePicker)v;return p.getYear()==target.get(Calendar.YEAR)&&p.getMonth()==target.get(Calendar.MONTH)&&p.getDayOfMonth()==target.get(Calendar.DAY_OF_MONTH);}
        public void describeTo(Description d){d.appendText("restored move destination");}
      }));
      onView(withId(android.R.id.button1)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click());drain();
      assertEquals(PlannerWeek.day(target),saved().day);assertEquals("Ăn nhẹ",saved().type);
      assertEquals(3,saved().servings,0.001);assertEquals(500,grocery(),0.001);
      onView(withId(R.id.next_week)).perform(click());
      option("Bỏ món");onView(withId(android.R.id.button1)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click());drain();
      assertEquals(1,repo.dao.mealSnapshot(null,null).stream().filter(m->m.recipeId==recipe.id).count());
      assertEquals(200,grocery(),0.001);assertNotNull(repo.dao.find(recipe.id));
    }
  }
  @Test public void nativeDragMovesOnlySelectedOccurrenceIntoLunch() throws Exception {
    try(ActivityScenario<MainActivity> s=open()){
      onData(sourceRow()).inAdapterView(withId(R.id.board_list)).check(matches(isDisplayed()));
      s.onActivity(a->((ListView)a.findViewById(R.id.board_list)).setSelection(0));onIdle();
      onView(withId(R.id.board_list)).perform(new ViewAction(){
        public Matcher<View> getConstraints(){return isAssignableFrom(ListView.class);}
        public String getDescription(){return "hold drag handle and drop on visible Lunch add row";}
        private void send(UiController c,long down,int action,float x,float y){
          MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0);
          event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
          try{assertTrue(c.injectMotionEvent(event));}catch(Exception e){throw new AssertionError(e);}finally{event.recycle();}
        }
        public void perform(UiController c,View view){
          ListView list=(ListView)view;View handle=null,target=null;
          for(int n=0;n<list.getChildCount();n++){
            BoardRow row=(BoardRow)list.getAdapter().getItem(list.getFirstVisiblePosition()+n);
            if(row.meal!=null&&row.meal.extraId==source.extraId)handle=list.getChildAt(n).findViewById(R.id.plan_drag_handle);
            if(row.meal==null&&!row.dayHeader&&row.day.equals(source.day)&&row.type.equals("Trưa"))target=list.getChildAt(n);
          }
          assertNotNull(handle);assertNotNull(target);int[] from=new int[2],to=new int[2];
          handle.getLocationOnScreen(from);target.getLocationOnScreen(to);
          float x=from[0]+handle.getWidth()/2f,y=from[1]+handle.getHeight()/2f;
          float tx=to[0]+target.getWidth()/2f,ty=to[1]+target.getHeight()/2f;
          long down=SystemClock.uptimeMillis();send(c,down,MotionEvent.ACTION_DOWN,x,y);c.loopMainThreadForAtLeast(700);
          for(int step=1;step<=8;step++){send(c,down,MotionEvent.ACTION_MOVE,x+(tx-x)*step/8,y+(ty-y)*step/8);c.loopMainThreadForAtLeast(60);}
          send(c,down,MotionEvent.ACTION_UP,tx,ty);c.loopMainThreadForAtLeast(400);
        }
      });drain();assertEquals("Trưa",saved().type);assertEquals(4,saved().servings,0.001);
      assertEquals(2,repo.dao.mealSnapshot(null,null).stream().filter(m->m.recipeId==recipe.id).count());
      assertEquals(600,grocery(),0.001);s.recreate();assertEquals("Trưa",saved().type);
      androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getUiAutomation().waitForIdle(1000,10000);
      Bitmap screenshot=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
      File qa=new File(context.getCacheDir(),"qa");assertTrue(qa.isDirectory()||qa.mkdirs());
      try(OutputStream out=new FileOutputStream(new File(qa,"planner-week.png"))){assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG,100,out));}
      finally{screenshot.recycle();}
    }
  }
}
