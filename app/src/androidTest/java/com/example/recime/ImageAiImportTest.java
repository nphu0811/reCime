package com.example.recime;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.onIdle;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static com.example.recime.UiGestures.click;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.*;

import android.content.*;
import android.graphics.*;
import android.os.SystemClock;
import android.widget.EditText;
import androidx.lifecycle.ViewModelProvider;
import androidx.test.core.app.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.recime.activity.MainActivity;
import com.example.recime.fragment.*;
import com.example.recime.viewmodel.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Real multipart HTTP to a local fixture; never contacts a paid provider. */
@RunWith(AndroidJUnit4.class)
public class ImageAiImportTest {
  @Test public void explicitConsentMultipartRotationAndPhotoReachEditor() throws Exception {
    exercise(false);
  }
  @Test public void missingKeyKeepsOriginalDraftAndPhotoAfterRotation() throws Exception {
    exercise(true);
  }

  private void exercise(boolean fail) throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    SharedPreferences prefs = context.getSharedPreferences("ai", 0);
    boolean had = prefs.contains("endpoint"); String old = prefs.getString("endpoint", "");
    File fixture = File.createTempFile("ai_import_qa", ".jpg", context.getCacheDir());
    Bitmap bitmap = Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888);
    bitmap.eraseColor(Color.WHITE);
    try (OutputStream out = new FileOutputStream(fixture)) { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out); }
    bitmap.recycle();
    android.media.ExifInterface exif = new android.media.ExifInterface(fixture.getPath());
    exif.setAttribute(android.media.ExifInterface.TAG_ORIENTATION, "6");
    exif.setAttribute(android.media.ExifInterface.TAG_IMAGE_DESCRIPTION, "private QA metadata");
    exif.saveAttributes();
    CountDownLatch received = new CountDownLatch(1), respond = new CountDownLatch(1);
    ExecutorService worker = Executors.newSingleThreadExecutor();
    try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
      server.setSoTimeout(25000);
      prefs.edit().putString("endpoint", "http://127.0.0.1:" + server.getLocalPort()).commit();
      Future<String> request = worker.submit(() -> {
        try (Socket socket = server.accept()) {
          socket.setSoTimeout(25000);
          InputStream input = new BufferedInputStream(socket.getInputStream());
          String first = line(input), header; int length = 0;
          while (!(header = line(input)).isEmpty()) {
            if (header.toLowerCase(java.util.Locale.ROOT).startsWith("content-length:"))
              length = Integer.parseInt(header.substring(15).trim());
          }
          assertTrue("Bounded multipart body", length > 0 && length < 8 * 1024 * 1024);
          byte[] bytes = new byte[length]; int count = 0, n;
          while (count < length && (n = input.read(bytes, count, length - count)) != -1) count += n;
          assertEquals(length, count);
          String body = new String(bytes, StandardCharsets.ISO_8859_1);
          assertTrue(body.contains("name=\"photo\""));
          assertFalse(body.contains("private QA metadata"));
          int start = body.indexOf("\r\n\r\n") + 4;
          Bitmap uploaded = BitmapFactory.decodeByteArray(bytes, start, bytes.length - start);
          assertNotNull(uploaded); assertEquals(40, uploaded.getWidth()); assertEquals(80, uploaded.getHeight()); uploaded.recycle();
          received.countDown(); assertTrue(respond.await(20, TimeUnit.SECONDS));
          String json = fail ? "{\"detail\":\"Configure OPENAI_API_KEY on backend\"}"
              : "{\"recipe\":{\"title\":\"QA Image Soup\",\"ingredients\":[\"200 g tofu\"],\"steps\":[\"Cook tofu.\"],\"servings\":2},\"requiresConfirmation\":true}";
          byte[] response = json.getBytes(StandardCharsets.UTF_8);
          OutputStream out = socket.getOutputStream();
          out.write(("HTTP/1.1 " + (fail ? "503 Service Unavailable" : "200 OK")
              + "\r\nContent-Type: application/json\r\nContent-Length: " + response.length
              + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
          out.write(response); out.flush(); return first;
        }
      });
      // The user may already have a task open; own a fresh task without clearing app data.
      Intent launch = new Intent(context, MainActivity.class)
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
      try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(launch)) {
        scenario.onActivity(a -> a.open(new ImportFragment())); onIdle();
        onView(withId(R.id.ai_extract_image)).perform(scrollTo(), click());
        onView(withId(R.id.status)).check(matches(withText(containsString("Chọn ảnh"))));
        scenario.onActivity(a -> {
          ImportFragment f = (ImportFragment) a.getSupportFragmentManager().findFragmentById(R.id.host);
          ImportImageState.Result ready = new ImportImageState.Result();
          ready.id = 1; ready.image = fixture.getAbsolutePath(); ready.text = "Original OCR draft";
          new ViewModelProvider(f).get(ImportImageState.class).result.setValue(ready);
        });
        onView(withId(R.id.ai_extract_image)).perform(scrollTo(), click());
        onView(withText("Hủy")).inRoot(isDialog()).perform(click());
        assertEquals("Cancel must not upload", 1, received.getCount());
        onView(withId(R.id.ai_extract_image)).perform(scrollTo(), click());
        onView(withText("Gửi ảnh")).inRoot(isDialog()).perform(click());
        assertTrue("Multipart must arrive", received.await(15, TimeUnit.SECONDS));
        scenario.recreate(); onView(withId(R.id.progress)).check(matches(isDisplayed()));
        respond.countDown(); assertEquals("POST /v1/import/ai-image HTTP/1.1", request.get(20, TimeUnit.SECONDS));
        long deadline = SystemClock.elapsedRealtime() + 15000;
        boolean[] busy = {true};
        do {
          scenario.onActivity(a -> busy[0] = new ViewModelProvider(a.getSupportFragmentManager()
              .findFragmentById(R.id.host)).get(ImportWebState.class).isLoading());
          if (!busy[0]) break;
          SystemClock.sleep(50);
        } while (SystemClock.elapsedRealtime() < deadline);
        assertFalse("Extraction must finish", busy[0]); onIdle();
        if (fail) {
          onView(withId(R.id.status)).check(matches(withText(containsString("Configure OPENAI_API_KEY"))));
          onView(withId(R.id.raw)).check(matches(withText("Original OCR draft")));
        } else {
          onView(withId(R.id.raw)).check(matches(withText(containsString("QA Image Soup"))));
          onView(withId(R.id.raw)).perform(scrollTo(), replaceText("Edited image draft"), closeSoftKeyboard());
          scenario.recreate();
          onView(withId(R.id.raw)).check(matches(withText("Edited image draft")));
        }
        onView(withId(R.id.review)).perform(scrollTo(), click());
        boolean[] opened = {false};
        deadline = SystemClock.elapsedRealtime() + 15000;
        do {
          scenario.onActivity(a -> opened[0] = a.getSupportFragmentManager().findFragmentById(R.id.host) instanceof EditorFragment);
          if (opened[0]) break;
          SystemClock.sleep(50);
        } while (SystemClock.elapsedRealtime() < deadline);
        assertTrue("Review must open Editor", opened[0]); onIdle();
        onView(withId(R.id.bottom)).check(matches(withEffectiveVisibility(Visibility.GONE)));
        scenario.onActivity(a -> {
          androidx.fragment.app.Fragment editor = a.getSupportFragmentManager().findFragmentById(R.id.host);
          assertTrue(editor instanceof EditorFragment);
          assertEquals("Selected image retained for review", fixture.getAbsolutePath(), editor.getArguments().getString("image"));
        });
      }
    } finally {
      respond.countDown(); worker.shutdownNow(); fixture.delete();
      if (had) prefs.edit().putString("endpoint", old).commit(); else prefs.edit().remove("endpoint").commit();
    }
  }

  private static String line(InputStream input) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream(); int c;
    while ((c = input.read()) != -1 && c != '\n') { if (c != '\r') out.write(c); if (out.size() > 8192) throw new IOException("Header too long"); }
    if (c == -1) throw new EOFException();
    return out.toString("US-ASCII");
  }
}
