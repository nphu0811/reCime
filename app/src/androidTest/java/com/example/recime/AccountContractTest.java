package com.example.recime;
import android.content.*;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.recime.AIservice.AccountClient;
import com.example.recime.helper.*;
import com.example.recime.model.Recipe;
import com.example.recime.sql.LabDatabase;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import static org.junit.Assert.*;

/** Requires the independent local FastAPI server forwarded on localhost:8765. No remote providers. */
@RunWith(AndroidJUnit4.class)
public class AccountContractTest {
  @Test public void realLocalAuthCloudOutboxConflictAndMergeContract() throws Exception {
    Context base = ApplicationProvider.getApplicationContext(); String prefix="contract"+System.nanoTime();
    Context c=new ContextWrapper(base){
      @Override public android.content.SharedPreferences getSharedPreferences(String name,int mode){return base.getSharedPreferences(prefix+name,mode);}
      @Override public File getNoBackupFilesDir(){File dir=new File(base.getNoBackupFilesDir(),prefix);dir.mkdirs();return dir;}
    };
    c.getSharedPreferences("ai",0).edit().putString("endpoint","http://127.0.0.1:8765").commit();
    AccountClient client=new AccountClient(c);String password="test-only-password-123";
    String email=prefix+"@example.invalid", dbName=prefix+".db"; LabDatabase db=new LabDatabase(base,dbName);
    try {
      JSONObject user=client.login(email,password,true); assertEquals(email,user.getString("email"));
      assertEquals(email,client.request("GET","/v1/account",null).getString("email"));
      assertNotEquals(new SessionStore(c).token(AccountClient.endpoint(c)),c.getSharedPreferences("account_session",0).getString("token",""));
      Recipe recipe=new Recipe();recipe.title="Cloud contract fixture";recipe.ingredients="100 g rice";recipe.steps="Cook";db.dao().insert(recipe);
      CloudBackupQueue.stage(c,db);assertTrue(CloudBackupQueue.pending(c));
      assertTrue(CloudBackupQueue.retry(c).contains("revision 1"));assertFalse(CloudBackupQueue.pending(c));
      JSONObject remote=client.request("GET","/v1/backup",null);BackupStore.validate(remote.getJSONObject("payload"));
      JSONObject stale=new JSONObject().put("operationId",java.util.UUID.randomUUID().toString()).put("baseRevision",0).put("payload",remote.getJSONObject("payload"));
      try{client.request("PUT","/v1/backup",stale);fail("Stale backup overwritten");}catch(IOException expected){assertTrue(expected.getMessage().contains("409"));}
      int count=CloudBackupQueue.downloadAndMerge(c,db);assertEquals(1,count);assertEquals(2,db.dao().recipeCount());
      assertEquals(0,CloudBackupQueue.downloadAndMerge(c,db));
      JSONObject change=new JSONObject().put("operationId",java.util.UUID.randomUUID().toString()).put("entity","grocery").put("entityId","contract-item").put("baseRevision",0).put("data",new JSONObject().put("checked",true));
      JSONObject saved=client.request("POST","/v1/sync/push",change);assertEquals(saved.toString(),client.request("POST","/v1/sync/push",change).toString());
      assertEquals(1,client.request("GET","/v1/sync/pull",null).getJSONArray("changes").length());
      client.logout(); assertTrue(new SessionStore(c).user().isEmpty());
      client.login(email,password,false);client.request("DELETE","/v1/account",new JSONObject().put("password",password));new SessionStore(c).clear();
    } finally {
      try { if(!new SessionStore(c).user().isEmpty())client.request("DELETE","/v1/account",new JSONObject().put("password",password)); } catch(Exception ignored){}
      CloudBackupQueue.cancel(c); db.close(); base.deleteDatabase(dbName);
      for(String name:new String[]{"ai","account_session","cloud_revision"})c.getSharedPreferences(name,0).edit().clear().commit();
      c.getNoBackupFilesDir().delete();
    }
  }
}
