package com.example.recime;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.junit.Assert.*;

import android.content.*;
import android.net.Uri;
import android.os.SystemClock;
import android.widget.TextView;
import androidx.core.content.FileProvider;
import androidx.test.core.app.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.recime.AIservice.LocalAiClient;
import com.example.recime.activity.MainActivity;
import java.io.*;
import java.util.concurrent.atomic.AtomicReference;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Requires the user's local server + adb reverse tcp:8765 tcp:8765. No paid service. */
@RunWith(AndroidJUnit4.class)
public class AiLaptopIntegrationTest {
  @Test
  public void androidCanCallRealRecipeModelOnLaptop() throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    LocalAiClient client = new LocalAiClient(context);
    assertTrue(client.health().getInt("recipes") >= 547);
    JSONArray results = client.text("bún bò Huế").getJSONArray("candidates");
    assertEquals("Bún bò Huế", results.getJSONObject(0).getString("title"));
    assertTrue(results.getJSONObject(0).getString("source").startsWith("https://"));
  }

  @Test
  public void sharedFoodPhotoUsesRealVisionServerThenOpensSourcedRecipe() throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (InputStream in = context.getAssets().open("catalog/recipes.json")) {
      byte[] buffer = new byte[8192];
      int n;
      while ((n = in.read(buffer)) != -1) bytes.write(buffer, 0, n);
    }
    JSONArray recipes = new JSONObject(bytes.toString("UTF-8")).getJSONArray("recipes");
    String image = "";
    for (int i = 0; i < recipes.length(); i++) {
      JSONObject r = recipes.getJSONObject(i);
      if (r.getString("title").equals("Bún bò Huế")) {
        image = r.getString("image");
        break;
      }
    }
    assertFalse(image.isEmpty());
    File dir = new File(context.getCacheDir(), "camera");
    dir.mkdirs();
    File file = new File(dir, "food-ai-test.jpg");
    try (InputStream in = context.getAssets().open("catalog/" + image);
        OutputStream out = new FileOutputStream(file)) {
      byte[] buffer = new byte[8192];
      int n;
      while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
    }
    Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".files", file);
    Intent intent =
        new Intent(context, MainActivity.class)
            .setAction(Intent.ACTION_SEND)
            .setType("image/jpeg")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(intent)) {
      AtomicReference<String> status = new AtomicReference<>("");
      long deadline = SystemClock.elapsedRealtime() + 120000;
      do {
        scenario.onActivity(
            a -> {
              TextView v = a.findViewById(R.id.status);
              if (v != null) status.set(v.getText().toString());
            });
        if (status.get().contains("Ảnh không thể xác minh")) break;
        SystemClock.sleep(200);
      } while (SystemClock.elapsedRealtime() < deadline);
      assertTrue(status.get(), status.get().contains("Ảnh không thể xác minh"));
      onView(withText("Món nào giống ảnh/caption của bạn?")).check(matches(isDisplayed()));
      onView(withText("Bún bò Huế")).perform(click());
      com.example.recime.sql.RecipeRepository.get(context)
          .io
          .submit(() -> {})
          .get(20, java.util.concurrent.TimeUnit.SECONDS);
      androidx.test.espresso.Espresso.onIdle();
      onView(withId(R.id.detail_title)).check(matches(withText("Bún bò Huế")));
    } finally {
      file.delete();
    }
  }
}
