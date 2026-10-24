package com.example.recime.viewmodel;

import android.os.Handler;
import android.os.Looper;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.example.recime.helper.ExtractionAdapter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/** Keeps one public-website import in flight across configuration changes. */
public final class ImportWebState extends ViewModel {
  public static final class Result {
    public final long id;
    public final String url, text, error;
    public final boolean loading;
    Result(long id, String url, String text, String error, boolean loading) {
      this.id = id;
      this.url = url;
      this.text = text;
      this.error = error;
      this.loading = loading;
    }
  }

  public final MutableLiveData<Result> result = new MutableLiveData<>();
  private final Handler main = new Handler(Looper.getMainLooper());
  private volatile long generation;
  private volatile boolean cleared;
  private Future<?> pending;

  public boolean isLoading() {
    Result value = result.getValue();
    return value != null && value.loading;
  }

  /** Called on the main thread. No Activity, Fragment, or View is retained. */
  public void fetch(String url, ExecutorService executor, ExtractionAdapter adapter) {
    long id = ++generation;
    if (pending != null) pending.cancel(true);
    result.setValue(new Result(id, url, null, null, true));
    try {
      pending = executor.submit(() -> {
        try {
          String text = adapter.extract(url);
          emit(new Result(id, url, text, null, false));
        } catch (Exception failure) {
          String message = failure.getMessage() == null
              ? failure.getClass().getSimpleName() : failure.getMessage();
          emit(new Result(id, url, null, message, false));
        }
      });
    } catch (RuntimeException rejection) {
      result.setValue(new Result(id, url, null,
          "Không thể bắt đầu tải trang. Thử lại.", false));
    }
  }

  private void emit(Result next) {
    main.post(() -> {
      if (!cleared && generation == next.id) result.setValue(next);
    });
  }

  @Override protected void onCleared() {
    cleared = true;
    ++generation;
    if (pending != null) pending.cancel(true);
  }
}
