package com.example.recime.service;
import android.app.job.*;
import com.example.recime.helper.CloudBackupQueue;
import java.util.concurrent.*;

public final class BackupRetryService extends JobService {
  private final ExecutorService io = Executors.newSingleThreadExecutor();
  @Override public boolean onStartJob(JobParameters parameters) {
    io.execute(() -> {
      boolean retry = false;
      try { CloudBackupQueue.retry(getApplicationContext()); }
      catch (Exception error) {
        String message = String.valueOf(error.getMessage());
        retry = !message.contains("HTTP 4") && !message.contains("khác");
      }
      jobFinished(parameters, retry);
    });
    return true;
  }
  @Override public boolean onStopJob(JobParameters parameters) { return true; }
  @Override public void onDestroy() { io.shutdown(); super.onDestroy(); }
}
