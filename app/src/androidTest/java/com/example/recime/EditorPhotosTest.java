package com.example.recime;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.onIdle;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.intent.Intents.*;
import static androidx.test.espresso.intent.matcher.IntentMatchers.hasAction;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.*;

import android.app.Activity;
import android.app.Instrumentation.ActivityResult;
import android.content.*;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.MediaStore;
import androidx.core.content.FileProvider;
import androidx.lifecycle.ViewModelProvider;
import androidx.test.core.app.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.recime.activity.MainActivity;
import com.example.recime.fragment.EditorFragment;
import com.example.recime.helper.RecipePhotoStore;
import com.example.recime.model.Recipe;
import com.example.recime.sql.*;
import com.example.recime.viewmodel.EditorSaveState;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.*;
import org.junit.runner.RunWith;

/** Picker result boundary is stubbed; URI decoding, file copies, UI, DB and rotation are real. */
@RunWith(AndroidJUnit4.class)
public class EditorPhotosTest {
  private final List<File> sources = new ArrayList<>();
  private final Context context = ApplicationProvider.getApplicationContext();
  @Before public void initIntents() { init(); }
  @After public void cleanup() { release(); for (File file : sources) file.delete(); }

  private Uri photo(int color) throws Exception {
    File dir = new File(context.getCacheDir(), "camera"); assertTrue(dir.isDirectory() || dir.mkdirs());
    File file = File.createTempFile("editor-test-", ".png", dir); sources.add(file);
    Bitmap bitmap = Bitmap.createBitmap(48, 32, Bitmap.Config.ARGB_8888);
    bitmap.eraseColor(0xff000000 | color);
    try (OutputStream out = new FileOutputStream(file)) { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)); }
    bitmap.recycle();
    return FileProvider.getUriForFile(context, context.getPackageName() + ".files", file);
  }

  private void pickerReturns(List<Uri> uris) {
    release(); init();
    Intent result = new Intent().addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    ClipData clip = ClipData.newUri(context.getContentResolver(), "owned test image", uris.get(0));
    for (int n = 1; n < uris.size(); n++) clip.addItem(new ClipData.Item(uris.get(n)));
    result.setData(uris.get(0)); result.setClipData(clip);
    intending(hasAction(MediaStore.ACTION_PICK_IMAGES)).respondWith(new ActivityResult(Activity.RESULT_OK, result));
  }

  private ArrayList<String> awaitPhotos(ActivityScenario<MainActivity> scenario, int count, String replaced) throws Exception {
    long deadline = System.currentTimeMillis() + 15000;
    ArrayList<String> snapshot = new ArrayList<>();
    while (System.currentTimeMillis() < deadline) {
      boolean[] ready = {false};
      scenario.onActivity(activity -> {
        EditorSaveState state = new ViewModelProvider(activity.getSupportFragmentManager().findFragmentById(R.id.host))
            .get(EditorSaveState.class);
        snapshot.clear(); snapshot.addAll(state.photos);
        ready[0] = !state.importingPhotos && snapshot.size() == count
            && (replaced == null || !snapshot.get(0).equals(replaced));
      });
      if (ready[0]) { onIdle(); return snapshot; }
      Thread.sleep(50);
    }
    throw new AssertionError("Photo operation did not finish");
  }

  @Test public void fivePickerPhotosPreviewRemoveReplaceRotateSaveAndReopen() throws Exception {
    RecipeRepository repo = RecipeRepository.get(context);
    repo.io.submit(() -> {}).get(30, TimeUnit.SECONDS);
    String title = "Photo editor " + System.nanoTime();
    List<String> savedPaths = new ArrayList<>();
    try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
      scenario.onActivity(a -> a.open(EditorFragment.create(0, null, "", "")));
      List<Uri> uris = new ArrayList<>();
      for (int n = 0; n < 5; n++) uris.add(photo(0x224466 + n * 0x101010));
      pickerReturns(uris);
      onView(withId(R.id.add_editor_photos)).perform(scrollTo(), click());
      ArrayList<String> picked = awaitPhotos(scenario, 5, null);
      onView(withId(R.id.photo_count)).check(matches(withText("5 / 5")));
      onView(withId(R.id.add_editor_photos)).check(matches(not(isEnabled())));
      onView(withContentDescription("Xem ảnh 1")).perform(scrollTo(), click());
      onView(withText("Đóng")).perform(click());
      onView(withContentDescription("Xóa ảnh 5")).perform(scrollTo(), click());
      awaitPhotos(scenario, 4, null);
      pickerReturns(Collections.singletonList(photo(0x22cc77)));
      onView(withId(R.id.add_editor_photos)).perform(scrollTo(), click());
      awaitPhotos(scenario, 5, null);
      intended(org.hamcrest.Matchers.allOf(hasAction(MediaStore.ACTION_PICK_IMAGES),
          not(androidx.test.espresso.intent.matcher.IntentMatchers.hasExtraWithKey(MediaStore.EXTRA_PICK_IMAGES_MAX))));
      onView(withContentDescription("Xóa ảnh 5")).perform(scrollTo(), click());
      awaitPhotos(scenario, 4, null);
      pickerReturns(Collections.singletonList(photo(0x99ccff)));
      onView(withContentDescription("Thay ảnh 1")).perform(scrollTo(), click());
      savedPaths.addAll(awaitPhotos(scenario, 4, picked.get(0)));
      for (File file : sources) file.delete(); // Picker grants/originals are no longer needed.
      for (String path : savedPaths) assertTrue(new File(path).isFile());
      scenario.recreate();
      assertEquals(savedPaths, awaitPhotos(scenario, 4, null));
      androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
          .getUiAutomation().waitForIdle(1000, 10000);
      Bitmap screenshot = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
          .getUiAutomation().takeScreenshot();
      assertNotNull(screenshot);
      File qa = new File(context.getCacheDir(), "qa"); assertTrue(qa.isDirectory() || qa.mkdirs());
      try (OutputStream out = new FileOutputStream(new File(qa, "editor-photos.png"))) {
        assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, out));
      }
      screenshot.recycle();
      onView(withId(R.id.edit_title)).perform(scrollTo(), replaceText(title), closeSoftKeyboard());
      onView(withId(R.id.edit_ingredients)).perform(scrollTo(), replaceText("200 g gạo"), closeSoftKeyboard());
      onView(withId(R.id.edit_steps)).perform(scrollTo(), replaceText("Nấu cơm."), closeSoftKeyboard());
      onView(withId(R.id.save)).perform(click());
      repo.io.submit(() -> {}).get(30, TimeUnit.SECONDS); onIdle();
      Recipe saved = null;
      for (Recipe r : repo.dao.recipeSnapshot()) if (title.equals(r.title)) saved = r;
      assertNotNull(saved); long id = saved.id;
      LabDatabase reopened = new LabDatabase(context);
      try { assertEquals(savedPaths, reopened.dao().find(id).photoPaths()); }
      finally { reopened.close(); }
      for (String path : savedPaths) {
        assertTrue(path.startsWith(new File(context.getFilesDir(), "photos").getAbsolutePath()));
        assertTrue("A committed photo must survive editor destruction", new File(path).isFile());
      }
      Recipe shared = new Recipe();
      shared.title = title + " shared";
      shared.setPhotoPaths(Collections.singletonList(savedPaths.get(0)));
      repo.io.submit(() -> shared.id = repo.dao.insert(shared)).get(30, TimeUnit.SECONDS);
      scenario.onActivity(a -> a.open(EditorFragment.create(id, null, "", "")));
      assertEquals(savedPaths, awaitPhotos(scenario, 4, null));
      for (int n = 0; n < 4; n++) onView(withContentDescription("Xóa ảnh 1")).perform(scrollTo(), click());
      onView(withId(R.id.save)).perform(click());
      repo.io.submit(() -> {}).get(30, TimeUnit.SECONDS); onIdle();
      assertTrue(repo.dao.find(id).photoPaths().isEmpty());
      assertEquals("", repo.dao.find(id).image);
      assertTrue("Another recipe still owns this photo", new File(savedPaths.get(0)).isFile());
      for (int n = 1; n < savedPaths.size(); n++) assertFalse(new File(savedPaths.get(n)).exists());
    } finally {
      repo.io.submit(() -> { for (Recipe r : repo.dao.recipeSnapshot())
          if (title.equals(r.title) || (title + " shared").equals(r.title)) repo.dao.delete(r); })
          .get(30, TimeUnit.SECONDS);
      for (String path : savedPaths) RecipePhotoStore.deleteOwned(context, path);
    }
  }

  @Test public void cancelDiscardsStagedCopiesAndInvalidBatchRollsBack() throws Exception {
    List<String> staged = new ArrayList<>();
    try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
      scenario.onActivity(a -> a.open(EditorFragment.create(0, null, "", "")));
      pickerReturns(Collections.singletonList(photo(0x55aa33)));
      onView(withId(R.id.add_editor_photos)).perform(scrollTo(), click());
      staged.addAll(awaitPhotos(scenario, 1, null));
      onView(withId(R.id.back)).perform(click()); onIdle();
      for (String path : staged) assertFalse(new File(path).exists());
    } finally { for (String path : staged) RecipePhotoStore.deleteOwned(context, path); }
    File invalid = File.createTempFile("invalid-", ".png", new File(context.getCacheDir(), "camera"));
    sources.add(invalid);
    try (FileOutputStream out = new FileOutputStream(invalid)) { out.write(new byte[]{1, 2, 3}); }
    File dir = new File(context.getFilesDir(), "photos");
    Set<String> before = new HashSet<>(Arrays.asList(dir.list()));
    try {
      RecipePhotoStore.copy(context, Arrays.asList(photo(0xaabbcc),
          FileProvider.getUriForFile(context, context.getPackageName() + ".files", invalid)));
      fail("An invalid image must reject the batch");
    } catch (IOException expected) { assertEquals(before, new HashSet<>(Arrays.asList(dir.list()))); }
  }
}
