package com.example.recime;
import static com.example.recime.UiGestures.click;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.Espresso.onIdle;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import android.content.*;
import androidx.test.core.app.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.recime.activity.MainActivity;
import com.example.recime.model.Recipe;
import com.example.recime.sql.RecipeRepository;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import java.util.concurrent.TimeUnit;
import org.junit.*;
import org.junit.runner.RunWith;
@RunWith(AndroidJUnit4.class)
public class LibraryUiTest {
  private RecipeRepository repo;
  private Recipe personal,catalog;
  private String prefix,book;
  @Before public void setup() throws Exception {
    repo=RecipeRepository.get(ApplicationProvider.getApplicationContext());idle();
    prefix="HomeQA"+System.nanoTime();book=prefix+" Book";
    personal=new Recipe();personal.title=prefix+" Personal";personal.ingredients="100 g rice";personal.steps="Cook";personal.cookbook=book;
    catalog=new Recipe();catalog.title=prefix+" Catalog";catalog.catalogKey=prefix;catalog.source="https://en.wikibooks.org/";catalog.ingredients="100 g rice";catalog.steps="Cook";catalog.cookbook=prefix+" CatalogBook";
    repo.io.submit(()->{personal.id=repo.dao.insert(personal);catalog.id=repo.dao.insert(catalog);}).get(20,TimeUnit.SECONDS);
  }
  private void idle() throws Exception {repo.io.submit(()->{}).get(20,TimeUnit.SECONDS);onIdle();}
  @After public void cleanup() throws Exception {
    repo.io.submit(()->{repo.dao.delete(personal);repo.dao.delete(catalog);
      for(String name:repo.dao.bookNamesSnapshot())if(name.startsWith(prefix))repo.dao.deleteCookbook(name);
    }).get(20,TimeUnit.SECONDS);
  }
  @Test public void fourStableTabsSeparateCatalogAndRestoreSearchOnRotationAndBack() throws Exception {
    try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)){
      s.onActivity(a->{BottomNavigationView nav=a.findViewById(R.id.bottom);assertEquals(4,nav.getMenu().size());assertEquals(1f,nav.getScaleX(),0);});
      onData(equalTo(book)).inAdapterView(withId(R.id.list)).perform(click());
      onView(withId(R.id.heading)).check(matches(withText(book)));
      pressBack();onView(withId(R.id.all_recipes)).perform(click());
      onView(withId(R.id.search)).perform(replaceText(prefix),closeSoftKeyboard());
      onView(allOf(withId(R.id.title),withText(personal.title))).check(matches(isDisplayed()));
      onView(withId(R.id.empty)).check(matches(withText("1 công thức")));
      onView(withId(R.id.nav_discover)).perform(click());
      onView(withId(R.id.heading)).check(matches(withText("Discover")));
      onView(withId(R.id.search)).perform(replaceText(prefix),closeSoftKeyboard());
      onView(allOf(withId(R.id.title),withText(catalog.title))).perform(click());
      onView(withId(R.id.detail_title)).check(matches(withText(catalog.title)));
      pressBack();s.recreate();onIdle();
      onView(withId(R.id.search)).check(matches(withText(prefix)));
      onView(withId(R.id.nav_plan)).perform(click());
      onView(withId(R.id.board_title)).check(matches(withText("Meal Plan")));
      onView(withId(R.id.nav_grocery)).perform(click());
      onView(withId(R.id.board_title)).check(matches(withText("Giỏ đi chợ")));
      onView(withId(R.id.nav_recipes)).perform(click());
      onView(withId(R.id.search)).check(matches(withText(prefix)));
      onView(allOf(withId(R.id.title),withText(personal.title))).check(matches(isDisplayed()));
      for(int cycle=0;cycle<2;cycle++){
        onView(withId(R.id.nav_plan)).perform(click());
        onView(withId(R.id.board_title)).check(matches(withText("Meal Plan")));
        onView(withId(R.id.next_week)).perform(click());onView(withId(R.id.prev_week)).perform(click());
        onView(withId(R.id.nav_discover)).perform(click());
        onView(withId(R.id.heading)).check(matches(withText("Discover")));
        onView(withId(R.id.nav_grocery)).perform(click());
        onView(withId(R.id.board_title)).check(matches(withText("Giỏ đi chợ")));
        onView(withId(R.id.nav_recipes)).perform(click());
        onView(withId(R.id.search)).check(matches(withText(prefix)));
      }
      onView(withId(R.id.ingredient_filter)).perform(replaceText("missing ingredient"),closeSoftKeyboard());
      onView(withId(R.id.empty)).check(matches(withText("Chưa có công thức phù hợp.")));
    }
  }
  @Test public void cookbookCreateValidateSelectRotateRenameDeleteKeepsRecipe() throws Exception {
    String created=prefix+" New",renamed=prefix+" Rename";
    try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)){
      onView(withId(R.id.new_book)).perform(click());onView(withText("Lưu")).perform(click());
      onView(withId(R.id.cookbook_name)).check(matches(hasErrorText("Tên không được trống")));
      onView(withId(R.id.cookbook_name)).perform(replaceText(created),closeSoftKeyboard());
      s.recreate();onIdle();onView(withId(R.id.cookbook_name)).check(matches(withText(created)));
      onView(withText("Lưu")).perform(click());idle();
      onData(equalTo(created)).inAdapterView(withId(R.id.list)).perform(click());
      onView(withId(R.id.cookbook_options)).perform(click());onView(withText("Chọn công thức")).perform(click());
      onData(equalTo(personal.title)).inAdapterView(withId(R.id.cookbook_members)).perform(click());
      s.recreate();onIdle();onView(withText("Lưu")).perform(click());idle();
      assertTrue(repo.dao.recipeIdsForCookbook(created).contains(personal.id));
      assertTrue(repo.dao.recipeIdsForCookbook(book).contains(personal.id));
      onView(withId(R.id.cookbook_options)).perform(click());onView(withText("Đổi tên")).perform(click());
      onView(withId(R.id.cookbook_name)).perform(replaceText(renamed),closeSoftKeyboard());
      onView(withText("Lưu")).perform(click());idle();onView(withId(R.id.heading)).check(matches(withText(renamed)));
      onView(withId(R.id.cookbook_options)).perform(click());onView(withText("Chọn công thức")).perform(click());
      onData(equalTo(personal.title)).inAdapterView(withId(R.id.cookbook_members)).perform(click());
      onView(withText("Lưu")).perform(click());idle();
      assertFalse(repo.dao.recipeIdsForCookbook(renamed).contains(personal.id));
      assertTrue(repo.dao.recipeIdsForCookbook(book).contains(personal.id));
      onView(withId(R.id.cookbook_options)).perform(click());onView(withText("Xóa cookbook (giữ công thức)")).perform(click());
      onView(withText("Xóa")).perform(click());idle();
      onView(withId(R.id.heading)).check(matches(withText("Recipes")));
      assertNotNull(repo.dao.find(personal.id));assertTrue(repo.dao.recipeIdsForCookbook(book).contains(personal.id));
      s.onActivity(a->{try{java.io.File dir=new java.io.File(a.getCacheDir(),"qa");dir.mkdirs();
        android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(a.findViewById(R.id.main).getWidth(),a.findViewById(R.id.main).getHeight(),android.graphics.Bitmap.Config.ARGB_8888);
        a.findViewById(R.id.main).draw(new android.graphics.Canvas(bitmap));
        try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(dir,"library.png"))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
      }catch(Exception e){throw new RuntimeException(e);}});
    }
  }
  @Test public void plusOpensRealImportAndShareTextRestoresEditedLink() throws Exception {
    try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)){
      onView(withId(R.id.add)).perform(click());onView(withText("Nhập link, ảnh, văn bản / AI")).perform(click());
      onView(withId(R.id.url)).perform(scrollTo(),replaceText("https://example.org/recipe"),closeSoftKeyboard());
      s.recreate();onIdle();onView(withId(R.id.url)).check(matches(withText("https://example.org/recipe")));
      pressBack();onView(withId(R.id.heading)).check(matches(withText("Recipes")));
    }
    Intent intent=new Intent(ApplicationProvider.getApplicationContext(),MainActivity.class).setAction(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,"https://example.org/shared");
    try(ActivityScenario<MainActivity> s=ActivityScenario.launch(intent)){
      onView(withId(R.id.url)).perform(scrollTo(),replaceText("https://example.org/edited"),closeSoftKeyboard());
      s.recreate();onIdle();onView(withId(R.id.url)).check(matches(withText("https://example.org/edited")));
      pressBack();onView(withId(R.id.heading)).check(matches(withText("Recipes")));
    }
  }
  @Test public void photoPickerCancelAndUnreadableSharedImageRemainRecoverable() throws Exception {
    androidx.test.espresso.intent.Intents.init();
    try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)){
      androidx.test.espresso.intent.Intents.intending(
          androidx.test.espresso.intent.matcher.IntentMatchers.hasAction(android.provider.MediaStore.ACTION_PICK_IMAGES))
          .respondWith(new android.app.Instrumentation.ActivityResult(android.app.Activity.RESULT_CANCELED,null));
      onView(withId(R.id.add)).perform(click());onView(withText("Nhập link, ảnh, văn bản / AI")).perform(click());
      onView(withId(R.id.pick)).perform(scrollTo(),click());
      androidx.test.espresso.intent.Intents.intended(
          androidx.test.espresso.intent.matcher.IntentMatchers.hasAction(android.provider.MediaStore.ACTION_PICK_IMAGES));
      onView(withId(R.id.review)).perform(scrollTo()).check(matches(isEnabled()));
    }finally{androidx.test.espresso.intent.Intents.release();}
    Context context=ApplicationProvider.getApplicationContext();
    android.net.Uri missing=androidx.core.content.FileProvider.getUriForFile(context,context.getPackageName()+".files",
        new java.io.File(context.getCacheDir(),"camera/missing-"+prefix+".png"));
    Intent shared=new Intent(context,MainActivity.class).setAction(Intent.ACTION_SEND).setType("image/png")
        .putExtra(Intent.EXTRA_STREAM,missing).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    try(ActivityScenario<MainActivity> s=ActivityScenario.launch(shared)){
      onView(withId(R.id.status)).perform(scrollTo(),new androidx.test.espresso.ViewAction(){
        public org.hamcrest.Matcher<android.view.View> getConstraints(){return isAssignableFrom(android.widget.TextView.class);}
        public String getDescription(){return "wait for recoverable shared image error";}
        public void perform(androidx.test.espresso.UiController controller,android.view.View view){
          long deadline=android.os.SystemClock.uptimeMillis()+15000;
          while(!((android.widget.TextView)view).getText().toString().contains("Không đọc được ảnh")){
            if(android.os.SystemClock.uptimeMillis()>deadline)throw new AssertionError("Image error did not appear");
            controller.loopMainThreadForAtLeast(50);
          }
        }
      });
      onView(withId(R.id.pick)).check(matches(isEnabled()));
      s.recreate();onIdle();onView(withId(R.id.pick)).perform(scrollTo()).check(matches(isEnabled()));
      pressBack();onView(withId(R.id.heading)).check(matches(withText("Recipes")));
    }
  }
}
