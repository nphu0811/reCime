package com.example.recime;

import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static com.example.recime.UiGestures.click;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.*;
import android.content.*;
import android.graphics.*;
import androidx.lifecycle.ViewModelProvider;
import androidx.test.core.app.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.recime.activity.MainActivity;
import com.example.recime.fragment.ImportFragment;
import com.example.recime.viewmodel.*;
import com.example.recime.sql.RecipeRepository;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CookingAiRotationTest {
  @Test public void jobSurvivesRotationAndReviewNeverSavesAutomatically() throws Exception {
    Context context=ApplicationProvider.getApplicationContext();
    SharedPreferences prefs=context.getSharedPreferences("ai",0);
    boolean had=prefs.contains("endpoint");String old=prefs.getString("endpoint","");
    File photo=File.createTempFile("cooking-rotation-", ".jpg",context.getCacheDir());
    Bitmap bitmap=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888);bitmap.eraseColor(Color.WHITE);
    try(FileOutputStream output=new FileOutputStream(photo)){bitmap.compress(Bitmap.CompressFormat.JPEG,90,output);}bitmap.recycle();
    CountDownLatch polled=new CountDownLatch(1),complete=new CountDownLatch(1);
    ExecutorService network=Executors.newSingleThreadExecutor();
    // A fresh AVD seeds the catalog on repository IO before the HTTP fixture starts.
    int before=RecipeRepository.get(context).io.submit(()->RecipeRepository.get(context).dao.recipeCount()).get(120,TimeUnit.SECONDS);
    try(ServerSocket server=new ServerSocket(0,2,InetAddress.getByName("127.0.0.1"))) {
      server.setSoTimeout(30000);
      prefs.edit().putString("endpoint","http://127.0.0.1:"+server.getLocalPort()).commit();
      Future<?> requests=network.submit(()->{
        try {
          for(int index=0;index<2;index++)try(Socket socket=server.accept()) {
            socket.setSoTimeout(30000);InputStream input=new BufferedInputStream(socket.getInputStream());
            String first=line(input),header;int length=0;
            while(!(header=line(input)).isEmpty())if(header.toLowerCase(java.util.Locale.ROOT).startsWith("content-length:"))length=Integer.parseInt(header.substring(15).trim());
            for(int consumed=0;consumed<length;){int n=input.read(new byte[Math.min(8192,length-consumed)]);if(n<0)throw new EOFException();consumed+=n;}
            String json;
            if(index==0){assertTrue(first.startsWith("POST /v1/cooking/jobs "));json="{\"id\":\"rotationjob\",\"state\":\"queued\"}";}
            else {
              assertTrue(first.startsWith("GET /v1/cooking/jobs/rotationjob "));polled.countDown();assertTrue(complete.await(25,TimeUnit.SECONDS));
              json="{\"id\":\"rotationjob\",\"state\":\"succeeded\",\"progress\":100,\"result\":{\"provider\":\"trained_local_vlm\",\"modelVersion\":\"contract-only\",\"scope\":\"image\",\"warnings\":[],\"recipe\":{\"is_cooking\":true,\"title\":\"QA Cooking Review\",\"language\":\"vi\",\"ingredients\":[{\"name\":\"rice\",\"quantity\":null,\"unit\":null,\"evidence\":{\"source\":\"visual\",\"text\":\"Visible rice\",\"start\":null,\"end\":null}}],\"steps\":[],\"uncertainty\":[\"Unknown amount\"],\"suggested_recipe\":null}}}";
            }
            byte[] bytes=json.getBytes(StandardCharsets.UTF_8);
            socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: "+bytes.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().write(bytes);socket.getOutputStream().flush();
          }
        }catch(Exception error){throw new RuntimeException(error);}
      });
      Intent intent=new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
      try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(intent)) {
        scenario.onActivity(a->a.open(new ImportFragment()));onIdle();
        scenario.onActivity(a->{
          ImportFragment fragment=(ImportFragment)a.getSupportFragmentManager().findFragmentById(R.id.host);
          ImportImageState.Result event=new ImportImageState.Result();event.id=1;event.image=photo.getAbsolutePath();event.text="Original safe draft";
          new ViewModelProvider(fragment).get(ImportImageState.class).result.setValue(event);
        });
        onView(withId(R.id.cooking_ai_image)).perform(scrollTo(),click());
        onView(withText("Model đã train trên backend riêng")).inRoot(isDialog()).perform(click());
        onView(withText("Đồng ý phân tích")).inRoot(isDialog()).perform(click());
        assertTrue(polled.await(20,TimeUnit.SECONDS));
        scenario.recreate();onIdle();complete.countDown();
        requests.get(20,TimeUnit.SECONDS);
        long deadline=android.os.SystemClock.elapsedRealtime()+10000;
        boolean ready=false;
        while(android.os.SystemClock.elapsedRealtime()<deadline) {
          try {onView(withText("Review công thức AI")).inRoot(isDialog()).check(matches(isDisplayed()));ready=true;break;}
          catch(androidx.test.espresso.NoMatchingViewException|androidx.test.espresso.NoMatchingRootException waiting){android.os.SystemClock.sleep(100);}
        }
        assertTrue("AI review appears after recreation",ready);
        onView(withText("Xem kết quả")).inRoot(isDialog()).perform(click());
        onView(withId(R.id.raw)).check(matches(withText(containsString("QA Cooking Review"))));
        onView(withId(R.id.raw)).check(matches(withText(containsString("chưa rõ lượng"))));
        int after=RecipeRepository.get(context).io.submit(()->RecipeRepository.get(context).dao.recipeCount()).get(20,TimeUnit.SECONDS);
        assertEquals("Review never inserts SQLite recipe",before,after);
      }
    } finally {
      complete.countDown();network.shutdownNow();photo.delete();
      if(had)prefs.edit().putString("endpoint",old).commit();else prefs.edit().remove("endpoint").commit();
    }
  }
  private static String line(InputStream input)throws IOException {
    ByteArrayOutputStream bytes=new ByteArrayOutputStream();int value;
    while((value=input.read())!=-1&&value!='\n')if(value!='\r')bytes.write(value);
    return bytes.toString("US-ASCII");
  }
}
