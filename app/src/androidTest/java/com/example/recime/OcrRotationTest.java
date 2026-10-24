package com.example.recime;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.onIdle;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.*;
import android.content.*;
import android.graphics.*;
import android.widget.TextView;
import androidx.test.core.app.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.lifecycle.ViewModelProvider;
import com.example.recime.activity.MainActivity;
import com.example.recime.fragment.ImportFragment;
import com.example.recime.viewmodel.ImportImageState;
import java.io.*;
import java.util.concurrent.*;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class OcrRotationTest {
  @Test public void pendingRealOcrSurvivesRotationAndDoesNotOverwriteSubsequentEdits() throws Exception {
    Context c=ApplicationProvider.getApplicationContext();File dir=new File(c.getCacheDir(),"camera");dir.mkdirs();File image=File.createTempFile("ocr_rotation_",".png",dir);
    Bitmap bitmap=Bitmap.createBitmap(1200,900,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);canvas.drawColor(Color.WHITE);
    Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);paint.setColor(Color.BLACK);paint.setTextSize(56);
    String[] lines={"Tofu bowl","Ingredients","200 g tofu","100 ml water","Instructions","Cook tofu in water."};
    for(int i=0;i<lines.length;i++)canvas.drawText(lines[i],50,100+i*120,paint);
    try(OutputStream out=new FileOutputStream(image)){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
    ExecutorService worker=Executors.newSingleThreadExecutor();CountDownLatch release=new CountDownLatch(1);
    worker.submit(()->{try{if(!release.await(20,TimeUnit.SECONDS))throw new AssertionError("Test gate timed out");}catch(InterruptedException e){Thread.currentThread().interrupt();}});
    android.net.Uri uri=androidx.core.content.FileProvider.getUriForFile(c,c.getPackageName()+".files",image);
    try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
      scenario.onActivity(a->a.open(new ImportFragment()));onIdle();
      scenario.onActivity(a->{androidx.fragment.app.Fragment f=a.getSupportFragmentManager().findFragmentById(R.id.host);new ViewModelProvider(f).get(ImportImageState.class).read(uri,false,worker);});
      onView(withId(R.id.pick)).check(matches(org.hamcrest.Matchers.not(isEnabled())));
      scenario.recreate();onIdle();onView(withId(R.id.pick)).check(matches(org.hamcrest.Matchers.not(isEnabled())));
      release.countDown();long deadline=android.os.SystemClock.elapsedRealtime()+30000;boolean[] ready={false};
      while(!ready[0]&&android.os.SystemClock.elapsedRealtime()<deadline){scenario.onActivity(a->ready[0]=a.findViewById(R.id.pick).isEnabled());Thread.sleep(50);}
      assertTrue("Real OCR must complete after rotation",ready[0]);
      onView(withId(R.id.raw)).check(matches(withText(containsString("200 g tofu"))));
      onView(withId(R.id.raw)).perform(scrollTo(),replaceText("Edited draft after OCR"),closeSoftKeyboard());
      scenario.recreate();onIdle();onView(withId(R.id.raw)).check(matches(withText("Edited draft after OCR")));
    }finally{release.countDown();worker.shutdown();worker.awaitTermination(30,TimeUnit.SECONDS);image.delete();}
  }
}
