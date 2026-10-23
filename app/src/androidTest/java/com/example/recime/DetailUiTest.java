package com.example.recime;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static com.example.recime.UiGestures.click;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.Espresso.onIdle;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import android.content.Context;
import android.graphics.Bitmap;
import androidx.test.core.app.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.recime.activity.MainActivity;
import com.example.recime.fragment.DetailFragment;
import com.example.recime.model.*;
import com.example.recime.sql.RecipeRepository;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.*;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class DetailUiTest {
  private final Context context=ApplicationProvider.getApplicationContext();
  private RecipeRepository repo;
  private Recipe fixture;
  private final List<String> files=new ArrayList<>();
  @Before public void setup() throws Exception {
    repo=RecipeRepository.get(context);drain();
    fixture=new Recipe();fixture.title="Detail QA "+System.nanoTime();
    fixture.ingredients="200 g gạo";fixture.steps="Rửa gạo.\nNấu cơm.";
    fixture.cookbook="Detail book "+System.nanoTime();
    repo.io.submit(()->fixture.id=repo.dao.insert(fixture)).get(30,TimeUnit.SECONDS);
  }
  @After public void cleanup() throws Exception {
    repo.io.submit(()->{repo.dao.delete(fixture);repo.dao.deleteCookbook(fixture.cookbook);})
        .get(30,TimeUnit.SECONDS);
    for(String path:files)new File(path).delete();
  }
  private void drain() throws Exception {repo.io.submit(()->{}).get(30,TimeUnit.SECONDS);onIdle();}
  private void photos(int count) throws Exception {
    File dir=new File(context.getFilesDir(),"photos");assertTrue(dir.isDirectory()||dir.mkdirs());
    for(int n=0;n<count;n++){
      File f=File.createTempFile("editor_", ".img", dir);
      Bitmap b=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888);b.eraseColor(0xff225588+n*0x111100);
      try(OutputStream out=new FileOutputStream(f)){assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,out));}
      b.recycle();files.add(f.getAbsolutePath());
    }
    fixture.setPhotoPaths(files);repo.io.submit(()->repo.dao.update(fixture)).get(30,TimeUnit.SECONDS);
  }
  private ActivityScenario<MainActivity> open(){
    ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class);
    s.onActivity(a->a.open(DetailFragment.create(fixture.id)));return s;
  }
  private void count(String value){onView(withId(R.id.detail_photo_count)).perform(scrollTo()).check(matches(withText(value)));}

  private void assertLastPhotoDecoded(ActivityScenario<MainActivity> scenario) throws Exception {
    long until=System.currentTimeMillis()+5000;
    while(System.currentTimeMillis()<until){
      boolean[] decoded={false};scenario.onActivity(a->{
        android.graphics.drawable.Drawable drawable=((android.widget.ImageView)a.findViewById(R.id.photo)).getDrawable();
        if(drawable instanceof android.graphics.drawable.BitmapDrawable)
          decoded[0]=((android.graphics.drawable.BitmapDrawable)drawable).getBitmap().getPixel(0,0)==0xff669988;
      });
      if(decoded[0])return;Thread.sleep(50);
    }
    fail("Selected gallery image must decode and display its own pixels");
  }
  @Test public void galleryFiveAndRotationKeepSelection() throws Exception {
    photos(5);
    try(ActivityScenario<MainActivity> s=open()){
      count("1 / 5");onView(withId(R.id.detail_photo_previous)).check(matches(not(isEnabled())));
      for(int n=0;n<4;n++)onView(withId(R.id.detail_photo_next)).perform(click());
      count("5 / 5");assertLastPhotoDecoded(s);onView(withId(R.id.detail_photo_next)).check(matches(not(isEnabled())));
      onView(withId(R.id.photo)).check(matches(withTagValue(is(files.get(4)))));
      s.recreate();count("5 / 5");
      android.app.UiAutomation automation=androidx.test.platform.app.InstrumentationRegistry
          .getInstrumentation().getUiAutomation();
      automation.waitForIdle(1000,10000);
      Bitmap screenshot=automation.takeScreenshot();assertNotNull(screenshot);
      File qa=new File(context.getCacheDir(),"qa");assertTrue(qa.isDirectory()||qa.mkdirs());
      try(OutputStream out=new FileOutputStream(new File(qa,"detail-gallery.png"))){
        assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG,100,out));
      } finally {screenshot.recycle();}
      onView(withId(R.id.photo)).check(matches(withTagValue(is(files.get(4)))));
      onView(withId(R.id.detail_photo_previous)).perform(click());count("4 / 5");
    }
  }
  @Test public void realEditReturnsUpdatedTitleIngredientsAndSelectedPhoto() throws Exception {
    photos(5);
    try(ActivityScenario<MainActivity> s=open()){
      count("1 / 5");onView(withId(R.id.detail_photo_next)).perform(click(),click());
      onView(withId(R.id.edit)).perform(scrollTo(),click());
      onView(withId(R.id.edit_title)).perform(scrollTo(),replaceText("Updated Detail QA"),closeSoftKeyboard());
      onView(withId(R.id.edit_ingredients)).perform(scrollTo(),replaceText("300 g gạo"),closeSoftKeyboard());
      onView(withContentDescription("Xóa ảnh 1")).perform(scrollTo(),click());
      onView(withId(R.id.save)).perform(click());drain();
      onView(withId(R.id.detail_title)).perform(scrollTo()).check(matches(withText("Updated Detail QA")));
      count("2 / 4");onView(withId(R.id.photo)).check(matches(withTagValue(is(files.get(2)))));
      onView(withId(R.id.detail_ingredients)).perform(scrollTo()).check(matches(withSubstring("300")));
      assertEquals(4,repo.dao.find(fixture.id).photoPaths().size());
      s.recreate();count("2 / 4");assertFalse(new File(files.get(0)).exists());
    }
  }
  @Test public void legacyCoverEmptyAndDeletedStateDoNotLeaveStaleActions() throws Exception {
    photos(1);fixture.images.clear();repo.io.submit(()->repo.dao.update(fixture)).get(30,TimeUnit.SECONDS);
    try(ActivityScenario<MainActivity> s=open()){
      count("1 / 1");onView(withId(R.id.detail_photo_next)).check(matches(not(isEnabled())));
      s.recreate();count("1 / 1");
      fixture.image="";repo.io.submit(()->repo.dao.update(fixture)).get(30,TimeUnit.SECONDS);onIdle();
      onView(withId(R.id.detail_photo_empty)).perform(scrollTo()).check(matches(isDisplayed()));
      repo.io.submit(()->repo.dao.delete(fixture)).get(30,TimeUnit.SECONDS);onIdle();
      onView(withId(R.id.detail_status)).check(matches(withText("Công thức không còn tồn tại")));
      onView(withId(R.id.detail_content)).check(matches(withEffectiveVisibility(Visibility.GONE)));
      s.recreate();onView(withId(R.id.detail_status)).check(matches(withText("Công thức không còn tồn tại")));
    }
  }
  @Test public void favoriteCookbooksAndConfirmedDeletePersistAndCleanOwnedPhoto() throws Exception {
    photos(1);String second="Other detail book "+System.nanoTime();
    repo.io.submit(()->repo.dao.createCookbook(second)).get(30,TimeUnit.SECONDS);
    try(ActivityScenario<MainActivity> s=open()){
      final Recipe[] observed={null};s.onActivity(a->{for(Recipe r:repo.dao.recipes().getValue())if(r.id==fixture.id)observed[0]=r;});
      onView(withId(R.id.favorite)).perform(scrollTo(),click());drain();
      assertFalse(observed[0].favorite);assertTrue(repo.dao.find(fixture.id).favorite);
      onView(withId(R.id.favorite)).check(matches(withText("♥ Đã thích")));
      onView(withId(R.id.add_to_book)).perform(scrollTo(),click());
      onData(is(second)).perform(click());onView(withText("Lưu")).perform(click());drain();
      assertTrue(repo.dao.cookbookNamesForRecipe(fixture.id).contains(second));
      s.recreate();onView(withId(R.id.favorite)).perform(scrollTo()).check(matches(withText("♥ Đã thích")));
      onView(withId(R.id.delete)).perform(scrollTo(),click());onView(withText("Giữ lại")).perform(click());
      assertNotNull(repo.dao.find(fixture.id));
      onView(withId(R.id.delete)).perform(click());
      onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click());drain();
      assertNull(repo.dao.find(fixture.id));assertFalse(new File(files.get(0)).exists());
      onView(withId(R.id.detail_title)).check(doesNotExist());
    } finally {repo.io.submit(()->repo.dao.deleteCookbook(second)).get(30,TimeUnit.SECONDS);}
  }
  @Test public void favoriteDoesNotOverwriteConcurrentRecipeEdit() throws Exception {
    photos(1);
    java.util.concurrent.CountDownLatch release=new java.util.concurrent.CountDownLatch(1);
    try(ActivityScenario<MainActivity> s=open()){
      onView(withId(R.id.favorite)).perform(scrollTo());
      repo.io.submit(()->{
        try { assertTrue(release.await(20,TimeUnit.SECONDS)); }
        catch(InterruptedException error){Thread.currentThread().interrupt();throw new IllegalStateException(error);}
        Recipe changed=repo.dao.find(fixture.id);changed.title="Concurrent edit retained";
        changed.steps="New instructions";repo.dao.update(changed);
      });
      onView(withId(R.id.favorite)).perform(click());release.countDown();drain();
      Recipe saved=repo.dao.find(fixture.id);assertTrue(saved.favorite);
      assertEquals("Concurrent edit retained",saved.title);assertEquals("New instructions",saved.steps);
      assertEquals(files,saved.photoPaths());
      onView(withId(R.id.detail_title)).perform(scrollTo()).check(matches(withText("Concurrent edit retained")));
    } finally {release.countDown();}
  }
  @Test public void failedDeleteKeepsDataAndRetryCompletesAfterRotation() throws Exception {
    photos(1);
    String trigger="detail_qa_delete_"+fixture.id;
    java.util.concurrent.CountDownLatch release=new java.util.concurrent.CountDownLatch(1);
    repo.io.submit(()->repo.db.getWritableDatabase().execSQL("CREATE TEMP TRIGGER "+trigger
        +" BEFORE DELETE ON recipes WHEN OLD.id="+fixture.id
        +" BEGIN SELECT RAISE(ABORT, 'QA deletion rejected'); END")).get(30,TimeUnit.SECONDS);
    try(ActivityScenario<MainActivity> s=open()){
      onView(withId(R.id.delete)).perform(scrollTo(),click());onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click());drain();
      onView(withId(R.id.detail_status)).perform(scrollTo()).check(matches(withSubstring("QA deletion rejected")));
      assertNotNull(repo.dao.find(fixture.id));assertTrue(new File(files.get(0)).exists());
      repo.io.submit(()->repo.db.getWritableDatabase().execSQL("DROP TRIGGER "+trigger)).get(30,TimeUnit.SECONDS);
      repo.io.submit(()->{try {release.await(20,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}});
      onView(withId(R.id.delete)).perform(scrollTo(),click());onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click());
      s.recreate();onView(withId(R.id.delete)).perform(scrollTo()).check(matches(not(isEnabled())));
      release.countDown();drain();assertNull(repo.dao.find(fixture.id));
      onView(withId(R.id.detail_title)).check(doesNotExist());
    } finally {
      release.countDown();repo.io.submit(()->repo.db.getWritableDatabase().execSQL("DROP TRIGGER IF EXISTS "+trigger)).get(30,TimeUnit.SECONDS);
    }
  }
  @Test public void groceriesScaleAndCookingReturnsToSameRecipe() throws Exception {
    String item="detailQA"+System.nanoTime();fixture.ingredients="200 g "+item;
    repo.io.submit(()->repo.dao.update(fixture)).get(30,TimeUnit.SECONDS);
    try(ActivityScenario<MainActivity> s=open()){
      onView(withId(R.id.plus)).perform(scrollTo(),click(),click());
      onView(withId(R.id.to_grocery)).perform(scrollTo(),click());drain();
      Grocery found=null;for(Grocery g:repo.dao.grocerySnapshot())if(g.name.equals(item))found=g;
      assertNotNull(found);assertEquals(400.0,found.amount,0.001);
      final Grocery saved=found;
      try {
        onView(withId(R.id.cooking_mode)).perform(scrollTo(),click());
        onView(withId(R.id.cook_step)).check(matches(withText("Rửa gạo.")));
        onView(withId(R.id.cook_next)).perform(click());
        onView(withId(R.id.cook_step)).check(matches(withText("Nấu cơm.")));
        s.recreate();onView(withId(R.id.cook_step)).check(matches(withText("Nấu cơm.")));
        onView(withId(R.id.cook_next)).perform(click());
        onView(withId(R.id.portions)).perform(scrollTo()).check(matches(withText("4 khẩu phần")));
      } finally {repo.io.submit(()->repo.dao.deleteGrocery(saved)).get(30,TimeUnit.SECONDS);}
    }
  }
}
