package com.example.recime;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.onIdle;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.intent.Intents.*;
import static androidx.test.espresso.intent.matcher.IntentMatchers.*;
import static org.junit.Assert.*;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.*;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.widget.EditText;
import androidx.test.core.app.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.recime.activity.MainActivity;
import com.example.recime.fragment.DetailFragment;
import com.example.recime.sql.RecipeRepository;
import com.example.recime.model.Recipe;
import com.example.recime.viewmodel.DetailActionState;
import androidx.lifecycle.ViewModelProvider;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import org.junit.*;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class DetailIntegrationTest {
  private final Context context=ApplicationProvider.getApplicationContext();
  private RecipeRepository repo;
  private Recipe recipe;
  @Before public void setup() throws Exception {
    repo=RecipeRepository.get(context);repo.io.submit(()->{}).get(30,TimeUnit.SECONDS);
    recipe=new Recipe();recipe.title="Detail export "+System.nanoTime();
    recipe.ingredients="100 g gạo";recipe.steps="Nấu cơm.";recipe.cookbook="";
    repo.io.submit(()->recipe.id=repo.dao.insert(recipe)).get(30,TimeUnit.SECONDS);
  }
  @After public void cleanup() throws Exception {
    repo.io.submit(()->repo.dao.delete(recipe)).get(30,TimeUnit.SECONDS);
    new File(context.getCacheDir(),"exports/recime-recipe-"+recipe.id+".pdf").delete();
  }
  private ActivityScenario<MainActivity> open(){
    ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class);
    s.onActivity(a->a.open(DetailFragment.create(recipe.id)));return s;
  }
  @Test public void pdfButtonSharesReadablePdfWithTemporaryGrant() throws Exception {
    init();
    try(ActivityScenario<MainActivity> s=open()){
      intending(hasAction(Intent.ACTION_CHOOSER)).respondWith(new Instrumentation.ActivityResult(Activity.RESULT_CANCELED,null));
      onView(withId(R.id.export_pdf)).perform(scrollTo(),click());
      intended(hasAction(Intent.ACTION_CHOOSER));
      Intent send=null;
      for(Intent intent:getIntents())if(Intent.ACTION_CHOOSER.equals(intent.getAction()))send=intent.getParcelableExtra(Intent.EXTRA_INTENT);
      assertNotNull(send);assertEquals("application/pdf",send.getType());
      assertTrue((send.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION)!=0);
      Uri stream=send.getParcelableExtra(Intent.EXTRA_STREAM);assertNotNull(stream);
      try(InputStream in=context.getContentResolver().openInputStream(stream)){
        byte[] header=new byte[5];assertEquals(5,in.read(header));assertEquals("%PDF-",new String(header,StandardCharsets.US_ASCII));
      }
      try(ParcelFileDescriptor fd=context.getContentResolver().openFileDescriptor(stream,"r");PdfRenderer pdf=new PdfRenderer(fd)){
        assertTrue(pdf.getPageCount()>0);
      }
    } finally {release();}
  }
  @Test public void printButtonOpensRealSystemPrintUi() throws Exception {
    Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
    try(ActivityScenario<MainActivity> s=open()){
      onView(withId(R.id.print_recipe)).perform(scrollTo(),click());
      boolean opened=false;long until=System.currentTimeMillis()+15000;
      while(System.currentTimeMillis()<until){
        android.view.accessibility.AccessibilityNodeInfo node=instrumentation.getUiAutomation().getRootInActiveWindow();
        if(node!=null && "com.android.printspooler".contentEquals(node.getPackageName())){opened=true;break;}
        Thread.sleep(100);
      }
      assertTrue("System print preview must open",opened);
      try(ParcelFileDescriptor command=instrumentation.getUiAutomation().executeShellCommand("input keyevent 4")){
        try(InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(command)){while(in.read()!=-1){}}
      }
      Thread.sleep(500);onIdle();
    }
  }
  @Test public void aiRejectsBlankAndShowsBackendFailureAfterRotation() throws Exception {
    SharedPreferences prefs=context.getSharedPreferences("ai",0);
    boolean had=prefs.contains("endpoint");String old=prefs.getString("endpoint","");
    CountDownLatch received=new CountDownLatch(1),respond=new CountDownLatch(1);
    ExecutorService serverThread=Executors.newSingleThreadExecutor();
    try(ServerSocket server=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))){
      server.setSoTimeout(20000);
      prefs.edit().putString("endpoint","http://127.0.0.1:"+server.getLocalPort()).commit();
      Future<String> request=serverThread.submit(()->{
        try(Socket socket=server.accept()){
          socket.setSoTimeout(20000);
          BufferedReader reader=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8));
          String first=reader.readLine(),line;
          while((line=reader.readLine())!=null&&!line.isEmpty()){}
          received.countDown();assertTrue(respond.await(20,TimeUnit.SECONDS));
          byte[] json="{\"detail\":\"QA backend unavailable\"}".getBytes(StandardCharsets.UTF_8);
          OutputStream out=socket.getOutputStream();
          out.write(("HTTP/1.1 503 Service Unavailable\r\nContent-Type: application/json\r\nContent-Length: "+json.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
          out.write(json);out.flush();return first;
        }
      });
      try(ActivityScenario<MainActivity> s=open()){
        onView(withId(R.id.ask_recipe_ai)).perform(scrollTo(),click());
        onView(withId(android.R.id.button1)).perform(click());
        onView(isAssignableFrom(EditText.class)).check(matches(hasErrorText("Nhập câu hỏi")));
        onView(isAssignableFrom(EditText.class)).perform(replaceText("How to cook?"),closeSoftKeyboard());
        onView(withId(android.R.id.button1)).perform(click());assertTrue(received.await(15,TimeUnit.SECONDS));
        s.recreate();respond.countDown();assertEquals("POST /assistant/ask HTTP/1.1",request.get(20,TimeUnit.SECONDS));
        long until=System.currentTimeMillis()+15000;
        while(System.currentTimeMillis()<until){
          boolean[] busy={true};s.onActivity(a->{
            androidx.fragment.app.Fragment f=a.getSupportFragmentManager().findFragmentById(R.id.host);
            busy[0]=new ViewModelProvider(f).get(DetailActionState.class).aiBusy;
          });
          if(!busy[0])break;Thread.sleep(50);
        }
        onIdle();onView(withText("Không thể hỏi AI: QA backend unavailable")).check(matches(isDisplayed()));
        onView(withText("Đóng")).perform(click());
      }
    } finally {
      respond.countDown();serverThread.shutdownNow();
      if(had)prefs.edit().putString("endpoint",old).commit();else prefs.edit().remove("endpoint").commit();
    }
  }
}
