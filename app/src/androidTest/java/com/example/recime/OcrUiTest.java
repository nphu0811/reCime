package com.example.recime;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.junit.Assert.*;

import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.SystemClock;
import android.widget.TextView;
import androidx.core.content.FileProvider;
import androidx.test.core.app.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.recime.activity.MainActivity;
import com.example.recime.model.Recipe;
import com.example.recime.sql.RecipeRepository;
import java.io.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class OcrUiTest {
  @Test
  public void sharedSelfCreatedImageGoesThroughOcrReviewAndSqlite() throws Exception {
    Context c = ApplicationProvider.getApplicationContext();
    RecipeRepository repo = RecipeRepository.get(c);
    File dir = new File(c.getCacheDir(), "camera");
    dir.mkdirs();
    File file = new File(dir, "ocr-test.png");
    Bitmap image = Bitmap.createBitmap(1200, 800, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(image);
    canvas.drawColor(Color.WHITE);
    Paint paint = new Paint(3);
    paint.setColor(Color.BLACK);
    paint.setTextSize(52);
    String[] text = {
      "OCR Test Tofu",
      "Ingredients",
      "200 g tofu",
      "100 ml water",
      "Instructions",
      "Cook tofu in water."
    };
    for (int n = 0; n < text.length; n++) canvas.drawText(text[n], 50, 100 + n * 100, paint);
    try (FileOutputStream out = new FileOutputStream(file)) {
      image.compress(Bitmap.CompressFormat.PNG, 100, out);
    }
    image.recycle();
    Uri uri = FileProvider.getUriForFile(c, c.getPackageName() + ".files", file);
    Intent intent =
        new Intent(c, MainActivity.class)
            .setAction(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    String title = "OCR Verified " + System.nanoTime();
    try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(intent)) {
      AtomicReference<String> status = new AtomicReference<>("");
      long deadline = SystemClock.elapsedRealtime() + 45000;
      do {
        scenario.onActivity(
            a -> {
              TextView v = a.findViewById(R.id.status);
              if (v != null) status.set(v.getText().toString());
            });
        if (status.get().contains("OCR hoàn tất")) break;
        SystemClock.sleep(150);
      } while (SystemClock.elapsedRealtime() < deadline);
      assertTrue(status.get(), status.get().contains("OCR hoàn tất"));
      onView(withId(R.id.raw)).check(matches(withSubstring("200")));
      onView(withId(R.id.review)).perform(scrollTo(), click());
      onView(withId(R.id.edit_title)).perform(scrollTo(), replaceText(title), closeSoftKeyboard());
      scenario.recreate();
      onView(withId(R.id.edit_title)).check(matches(withText(title)));
      onView(withId(R.id.save)).perform(scrollTo(), click());
      repo.io.submit(() -> {}).get(20, TimeUnit.SECONDS);
      Recipe saved =
          repo.io
              .submit(
                  () -> {
                    for (Recipe r : repo.dao.recipeSnapshot()) if (r.title.equals(title)) return r;
                    return null;
                  })
              .get(20, TimeUnit.SECONDS);
      assertNotNull(saved);
      assertTrue(saved.ingredients, saved.ingredients.contains("200"));
      assertTrue(new File(saved.image).isFile());
    } finally {
      repo.io
          .submit(
              () -> {
                for (Recipe r : repo.dao.recipeSnapshot())
                  if (r.title.equals(title)) repo.dao.delete(r);
              })
          .get(20, TimeUnit.SECONDS);
      file.delete();
    }
  }
}
