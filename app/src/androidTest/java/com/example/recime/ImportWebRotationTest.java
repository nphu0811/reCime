package com.example.recime;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static com.example.recime.UiGestures.click;
import static org.junit.Assert.*;

import android.os.SystemClock;
import android.widget.EditText;
import androidx.lifecycle.ViewModelProvider;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.recime.activity.MainActivity;
import com.example.recime.fragment.ImportFragment;
import com.example.recime.viewmodel.ImportWebState;
import com.example.recime.viewmodel.LabViewModel;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ImportWebRotationTest {
  @Test public void completedWebImportSurvivesRotationWithoutClobberingEdits() throws Exception {
    final String parsed = "QA Web Import\nIngredients\n200 g rice\nInstructions\nCook rice.";
    CountDownLatch started = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
      onView(withId(R.id.add)).perform(click());
      onView(withText("Nhập link, ảnh, văn bản / AI")).perform(click());
      scenario.onActivity(activity -> {
        ImportFragment fragment = (ImportFragment) activity.getSupportFragmentManager()
            .findFragmentById(R.id.host);
        ImportWebState web = new ViewModelProvider(fragment).get(ImportWebState.class);
        LabViewModel vm = new ViewModelProvider(activity).get(LabViewModel.class);
        web.fetch("https://recipes.example/rotation", vm.repository.extraction, url -> {
          started.countDown();
          if (!release.await(15, TimeUnit.SECONDS))
            throw new IllegalStateException("Synthetic response timed out");
          return parsed;
        });
      });
      assertTrue("Extractor should start", started.await(10, TimeUnit.SECONDS));
      onView(withId(R.id.progress)).check(matches(isDisplayed()));
      scenario.recreate();
      onView(withId(R.id.progress)).check(matches(isDisplayed()));
      release.countDown();

      AtomicReference<String> text = new AtomicReference<>("");
      long deadline = SystemClock.elapsedRealtime() + 15000;
      do {
        scenario.onActivity(activity -> {
          EditText input = activity.findViewById(R.id.raw);
          text.set(input == null ? "" : input.getText().toString());
        });
        if (parsed.equals(text.get())) break;
        SystemClock.sleep(100);
      } while (SystemClock.elapsedRealtime() < deadline);
      assertEquals("Result must appear in new view", parsed, text.get());
      onView(withId(R.id.progress)).check(matches(withEffectiveVisibility(
          Visibility.GONE)));
      onView(withId(R.id.raw)).perform(scrollTo(),
          replaceText("Edited recipe draft"), closeSoftKeyboard());
      scenario.recreate();
      onView(withId(R.id.raw)).check(matches(withText("Edited recipe draft")));
    } finally {
      release.countDown();
    }
  }
}
