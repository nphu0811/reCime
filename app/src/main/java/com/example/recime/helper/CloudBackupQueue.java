package com.example.recime.helper;

import android.content.Context;
import android.util.AtomicFile;
import com.example.recime.AIservice.AccountClient;
import com.example.recime.sql.LabDatabase;
import java.io.*;
import org.json.*;

/** Durable single-item backup outbox. Retries reuse the same operation id and revision. */
public final class CloudBackupQueue {
  private static File path(Context c) { return new File(c.getNoBackupFilesDir(), "cloud-backup-pending.json"); }
  private static String scope(Context c) throws Exception { return BackupStore.digest((AccountClient.endpoint(c) + ":" + new SessionStore(c).user()).getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
  public static synchronized boolean pending(Context c) { return path(c).isFile(); }
  public static synchronized void stage(Context c, LabDatabase db) throws Exception {
    if (pending(c)) throw new IOException("Còn backup đang chờ; gửi lại hoặc hủy bản chờ trước");
    if (new SessionStore(c).token(AccountClient.endpoint(c)).isEmpty()) throw new IOException("Đăng nhập trước khi lưu cloud");
    JSONObject payload = BackupStore.export(c, db);
    JSONObject body = new JSONObject().put("operationId", java.util.UUID.randomUUID().toString())
        .put("baseRevision", c.getSharedPreferences("cloud_revision", 0).getLong(scope(c), 0)).put("payload", payload);
    if (body.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 8 * 1024 * 1024)
      throw new IOException("Cloud tối đa 8MB; xuất backup tệp để giữ toàn bộ ảnh");
    JSONObject queue = new JSONObject().put("scope", scope(c)).put("body", body);
    AtomicFile file = new AtomicFile(path(c)); FileOutputStream out = null;
    try { out = file.startWrite(); out.write(queue.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)); file.finishWrite(out); }
    catch (Exception error) { if (out != null) file.failWrite(out); throw error; }
  }
  public static synchronized String retry(Context c) throws Exception {
    if (!pending(c)) return "Không có backup chờ";
    JSONObject queue;
    try (InputStream in = new AtomicFile(path(c)).openRead()) {
      queue = new JSONObject(new String(BackupStore.read(in, 9 * 1024 * 1024), java.nio.charset.StandardCharsets.UTF_8));
    }
    if (!queue.getString("scope").equals(scope(c))) throw new IOException("Backup chờ thuộc tài khoản/backend khác; đăng nhập đúng tài khoản hoặc hủy bản chờ");
    JSONObject result = new AccountClient(c).request("PUT", "/v1/backup", queue.getJSONObject("body"));
    c.getSharedPreferences("cloud_revision", 0).edit().putLong(scope(c), result.getLong("revision")).commit();
    new AtomicFile(path(c)).delete();
    return "Đã lưu cloud backup · revision " + result.getLong("revision");
  }
  public static synchronized void cancel(Context c) { new AtomicFile(path(c)).delete(); }
  public static synchronized int downloadAndMerge(Context c, LabDatabase db) throws Exception {
    if (pending(c)) throw new IOException("Hủy bản backup chờ trước khi tải và hợp nhất cloud");
    JSONObject result = new AccountClient(c).request("GET", "/v1/backup", null);
    int count = BackupStore.restore(c, db, result.getJSONObject("payload"));
    c.getSharedPreferences("cloud_revision", 0).edit().putLong(scope(c), result.getLong("revision")).commit();
    return count;
  }
  public static void schedule(Context context) {
    android.app.job.JobScheduler scheduler = (android.app.job.JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
    scheduler.schedule(new android.app.job.JobInfo.Builder(602, new android.content.ComponentName(context, com.example.recime.service.BackupRetryService.class))
        .setRequiredNetworkType(android.app.job.JobInfo.NETWORK_TYPE_ANY).setMinimumLatency(10000)
        .setBackoffCriteria(30000, android.app.job.JobInfo.BACKOFF_POLICY_EXPONENTIAL).build());
  }
  private CloudBackupQueue() {}
}
