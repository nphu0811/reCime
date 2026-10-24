package com.example.recime;

import static org.junit.Assert.*;
import android.content.Context;
import android.content.SharedPreferences;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.recime.AIservice.LocalAiClient;
import com.example.recime.helper.CookingRecipeReview;
import com.example.recime.helper.RecipeParser;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Actual multipart/poll/cancel contract, deterministic local fixture, no provider charges. */
@RunWith(AndroidJUnit4.class)
public class CookingAiContractTest {
  @Test public void multipartProviderConsentAndIdempotencyAreExplicit() throws Exception {
    Context context=ApplicationProvider.getApplicationContext();
    SharedPreferences prefs=context.getSharedPreferences("ai",0);
    boolean had=prefs.contains("endpoint"); String previous=prefs.getString("endpoint","");
    File media=File.createTempFile("cooking-contract-", ".mp4",context.getCacheDir());
    try(FileOutputStream out=new FileOutputStream(media)){out.write("local-contract-fixture".getBytes(StandardCharsets.US_ASCII));}
    ExecutorService worker=Executors.newSingleThreadExecutor();
    try(ServerSocket server=new ServerSocket(0,3,InetAddress.getByName("127.0.0.1"))) {
      server.setSoTimeout(15000);
      prefs.edit().putString("endpoint","http://127.0.0.1:"+server.getLocalPort()).commit();
      Future<?> network=worker.submit(()->{
        try {
          for(int index=0;index<3;index++) try(Socket socket=server.accept()) {
            socket.setSoTimeout(15000);
            InputStream input=new BufferedInputStream(socket.getInputStream());
            String first=line(input),header; int length=0; StringBuilder headers=new StringBuilder();
            while(!(header=line(input)).isEmpty()) {
              headers.append(header).append('\n');
              if(header.toLowerCase(java.util.Locale.ROOT).startsWith("content-length:"))length=Integer.parseInt(header.substring(15).trim());
            }
            byte[] bytes=new byte[length]; int count=0,n;
            while(count<length&&(n=input.read(bytes,count,length-count))!=-1)count+=n;
            assertEquals(length,count);
            if(index==0) {
              assertTrue(first.startsWith("POST /v1/cooking/jobs "));
              assertTrue(headers.toString().contains("Idempotency-Key: contract-key-123"));
              String body=new String(bytes,StandardCharsets.UTF_8);
              int field=body.indexOf("name=\"external_consent\"");
              assertTrue("Consent field present",field>=0);
              int content=body.indexOf("\r\n\r\n",field);
              assertTrue("Consent field body present",content>=field);
              assertTrue("Explicit true consent value",body.substring(content+4).startsWith("true\r\n"));
              assertTrue(body.contains("openai_existing"));
              assertTrue(body.contains("local-contract-fixture"));
            } else if(index==1)assertTrue(first.startsWith("GET /v1/cooking/jobs/abc "));
            else assertTrue(first.startsWith("POST /v1/cooking/jobs/abc/cancel "));
            byte[] json=("{\"id\":\"abc\",\"state\":\""+(index==2?"cancelled":"queued")+"\"}").getBytes(StandardCharsets.UTF_8);
            socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: "+json.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().write(json);socket.getOutputStream().flush();
          }
        }catch(Exception failure){throw new RuntimeException(failure);}
      });
      LocalAiClient client=new LocalAiClient(context);
      try { assertEquals("abc",client.cookingUpload(media,true,"openai_existing",true,"contract-key-123").getString("id")); }
      catch(IOException failure) { network.get(2,TimeUnit.SECONDS); throw failure; }
      assertEquals("queued",client.cookingStatus("abc").getString("state"));
      assertEquals("cancelled",client.cancelCooking("abc").getString("state"));
      network.get(20,TimeUnit.SECONDS);
    } finally {
      if(had)prefs.edit().putString("endpoint",previous).commit();else prefs.edit().remove("endpoint").commit();
      media.delete();worker.shutdownNow();
    }
  }

  @Test public void reviewPreservesUnknownAndProvenanceWithoutTurningNotesIntoSteps() throws Exception {
    JSONObject evidence=new JSONObject().put("source","visual").put("text","Visible rice").put("start",JSONObject.NULL).put("end",JSONObject.NULL);
    JSONObject recipe=new JSONObject().put("is_cooking",true).put("title","Rice").put("language","vi")
        .put("ingredients",new JSONArray().put(new JSONObject().put("name","rice").put("quantity",JSONObject.NULL).put("unit",JSONObject.NULL).put("evidence",evidence)))
        .put("steps",new JSONArray()).put("uncertainty",new JSONArray().put("Unknown quantity")).put("suggested_recipe",JSONObject.NULL);
    String text=CookingRecipeReview.render(new JSONObject().put("recipe",recipe).put("provider","trained_local_vlm").put("modelVersion","pilot").put("scope","image"));
    com.example.recime.model.ParsedRecipe parsed=RecipeParser.parse(text);
    assertTrue(parsed.ingredients.contains("chưa rõ lượng"));
    assertEquals("",parsed.steps);
    assertTrue(parsed.attribution.contains("Unknown quantity"));
    assertTrue(parsed.attribution.contains("pilot"));
    recipe.put("is_cooking",false);
    try {CookingRecipeReview.render(new JSONObject().put("recipe",recipe));fail("Non-cooking media cannot become a recipe");}
    catch(JSONException expected) {assertTrue(expected.getMessage().contains("không phải nấu ăn"));}
  }

  private static String line(InputStream input)throws IOException {
    ByteArrayOutputStream bytes=new ByteArrayOutputStream();int value;
    while((value=input.read())!=-1&&value!='\n')if(value!='\r')bytes.write(value);
    return bytes.toString("US-ASCII");
  }
}
