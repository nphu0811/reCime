package com.example.recime.viewmodel;

import android.app.Application;
import android.net.Uri;
import androidx.lifecycle.*;
import com.example.recime.AIservice.LocalAiClient;
import com.example.recime.helper.CookingRecipeReview;
import java.io.*;
import java.util.concurrent.*;
import org.json.*;

/** Saved job IDs resume polling after rotation/process recreation; never saves a recipe. */
public final class CookingImportState extends AndroidViewModel {
  private final SavedStateHandle state;
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private final ExecutorService controls = Executors.newSingleThreadExecutor();
  private final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
  public final MutableLiveData<String> status = new MutableLiveData<>("");
  public final MutableLiveData<String> review;
  public final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
  private volatile boolean cancelled, closed;
  private volatile String job = "";
  private volatile File file;
  private boolean video, consent;
  private String provider, key;
  private String scope;

  private String currentScope() {
    return com.example.recime.AIservice.AccountClient.endpoint(getApplication()) + "\n"
        + new com.example.recime.helper.SessionStore(getApplication()).user();
  }
  private void checkScope() throws IOException {
    if (scope == null || !scope.equals(currentScope())) throw new IOException("Tài khoản/backend đã đổi; chọn lại media và xác nhận nơi gửi");
  }

  public CookingImportState(Application app, SavedStateHandle state) {
    super(app); this.state = state; review = state.getLiveData("review", "");
    scope = state.get("scope");
    String pending = state.get("job");
    if (pending != null && !pending.isEmpty()) {
      job = pending; loading.setValue(true);
      worker.execute(this::restorePolling);
    } else {
      String cached = state.get("mediaFile");
      String requestKey = state.get("requestKey");
      if (cached != null && requestKey != null && new File(cached).isFile()) {
        file = new File(cached); key = requestKey;
        video = Boolean.TRUE.equals(state.get("video"));
        consent = Boolean.TRUE.equals(state.get("consent")); provider = state.get("provider");
        message("Có upload AI bị gián đoạn; chọn Thử lại để dùng lại yêu cầu đã đồng ý.");
      }
    }
  }

  public boolean isLoading() { return Boolean.TRUE.equals(loading.getValue()); }
  private void message(String value) { if (!closed) status.postValue(value); }
  private void rememberJob(String value) { main.post(() -> { if (!closed) state.set("job", value); }); }

  public void start(Uri uri, boolean video, String provider, boolean consent) {
    if (isLoading()) return;
    this.video = video; this.provider = provider; this.consent = consent;
    try { scope = currentScope(); }
    catch (RuntimeException invalid) { message("Địa chỉ backend không hợp lệ; sửa trong Cài đặt."); return; }
    state.set("scope", scope);
    key = java.util.UUID.randomUUID().toString(); cancelled = false; job = "";
    state.set("requestKey", key); state.set("video", video); state.set("provider", provider);
    state.set("consent", consent); state.set("mediaFile", null);
    state.set("job", ""); state.set("review", ""); loading.setValue(true);
    worker.execute(() -> {
      try {
        File directory = new File(getApplication().getCacheDir(), "cooking-import"); directory.mkdirs();
        // This directory contains only temporary uploads, never recipe photos or SQLite data.
        File[] stale = directory.listFiles();
        if (stale != null) for (File old : stale) if (old.lastModified() < System.currentTimeMillis() - 86400000L) old.delete();
        file = File.createTempFile("upload-", video ? ".mp4" : ".jpg", directory);
        try (InputStream input = getApplication().getContentResolver().openInputStream(uri);
             OutputStream output = new FileOutputStream(file)) {
          if (input == null) throw new IOException("Không truy cập được media; hãy chọn lại tệp có quyền sử dụng");
          byte[] buffer = new byte[65536]; int n; long total = 0;
          while ((n = input.read(buffer)) != -1) {
            if (cancelled || closed) throw new InterruptedIOException("Đã hủy");
            if ((total += n) > 10 * 1024 * 1024) throw new IOException("Media tối đa 10MB; video tối đa 180 giây");
            output.write(buffer, 0, n);
          }
        }
        CountDownLatch remembered = new CountDownLatch(1);
        main.post(() -> {
          if (!closed && !cancelled) state.set("mediaFile", file.getAbsolutePath());
          remembered.countDown();
        });
        if (!remembered.await(5, TimeUnit.SECONDS)) throw new IOException("Không lưu được trạng thái upload; thử lại");
        upload();
      } catch (Exception error) { fail(error); }
    });
  }

  private void upload() {
    try {
      if (cancelled || closed) return;
      checkScope();
      message("Đang tải media lên backend đã chọn…");
      LocalAiClient client = new LocalAiClient(getApplication());
      JSONObject created = client.cookingUpload(file, video, provider, consent, key);
      job = created.getString("id"); rememberJob(job);
      if (cancelled || closed) { client.cancelCooking(job); return; }
      poll(client);
    } catch (Exception error) { fail(error); }
    finally { if (!closed) loading.postValue(false); else if (file != null) file.delete(); }
  }

  private void restorePolling() {
    try { poll(new LocalAiClient(getApplication())); }
    catch (Exception error) { fail(error); }
  }

  private void poll(LocalAiClient client) {
    try {
      long deadline = android.os.SystemClock.elapsedRealtime() + 620000;
      while (!cancelled && !closed) {
        checkScope();
        JSONObject value = client.cookingStatus(job);
        String phase = value.getString("state");
        message("Cooking AI: " + phase + " · " + value.optInt("progress") + "%");
        if (phase.equals("succeeded")) {
          String raw = CookingRecipeReview.render(value.getJSONObject("result"));
          main.post(() -> { if (!closed) { state.set("review", raw); state.set("job", ""); state.set("mediaFile", null); } });
          job = ""; if (file != null) file.delete();
          return;
        }
        if (phase.equals("failed") || phase.equals("cancelled") || phase.equals("interrupted")) {
          rememberJob(""); job = ""; key = java.util.UUID.randomUUID().toString();
          final String retryKey = key;
          main.post(() -> { if (!closed) state.set("requestKey", retryKey); });
          throw new IOException(value.optString("error", phase) + ". Chọn Thử lại hoặc chọn lại media.");
        }
        if (android.os.SystemClock.elapsedRealtime() > deadline) throw new IOException("Hết thời gian chờ; Thử lại để kiểm tra job đang lưu");
        Thread.sleep(1200);
      }
    } catch (Exception error) { fail(error); }
    finally { if (!closed) loading.postValue(false); }
  }

  private void fail(Exception error) { message("Không phân tích được: " + (error.getMessage() == null ? "Kiểm tra kết nối/backend" : error.getMessage()) + ". Nháp vẫn được giữ."); loading.postValue(false); }
  public void retry() {
    if (isLoading()) return;
    cancelled = false; loading.setValue(true);
    worker.execute(() -> {
      if (!job.isEmpty()) restorePolling();
      else if (file != null && file.isFile()) upload();
      else { message("Hãy chọn lại media; quyền chia sẻ cũ có thể đã hết"); loading.postValue(false); }
    });
  }
  public void consumeReview() { state.set("review", ""); }
  public void cancel() {
    cancelled = true;
    controls.execute(() -> {
      try { if (!job.isEmpty()) { checkScope(); new LocalAiClient(getApplication()).cancelCooking(job); } }
      catch (Exception error) { message("Đã dừng chờ; chưa xác nhận hủy trên server. Kiểm tra lại kết nối."); return; }
      message("Đã hủy phân tích. Nháp vẫn được giữ.");
      rememberJob(""); job = "";
      key = java.util.UUID.randomUUID().toString();
      final String retryKey = key;
      main.post(() -> { if (!closed) state.set("requestKey", retryKey); });
    });
  }
  @Override protected void onCleared() {
    cancel(); closed = true;
    worker.execute(() -> { if (file != null) file.delete(); });
    worker.shutdown(); controls.shutdown();
  }
}
