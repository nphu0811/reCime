package com.example.recime.viewmodel;
import android.app.Application;
import androidx.lifecycle.*;
import java.util.concurrent.*;

/** Lifecycle-safe account/backup operations; no Fragment or view retained. */
public final class SettingsState extends AndroidViewModel {
  public interface Task { String run(Application context) throws Exception; }
  public final MutableLiveData<String> status = new MutableLiveData<>("Guest · dữ liệu được lưu trên thiết bị");
  public final MutableLiveData<Boolean> busy = new MutableLiveData<>(false);
  private final ExecutorService io = Executors.newSingleThreadExecutor();
  public SettingsState(Application application) { super(application); }
  public void run(Task task) {
    if (Boolean.TRUE.equals(busy.getValue())) return;
    busy.setValue(true); status.setValue("Đang xử lý…");
    io.execute(() -> {
      try { status.postValue(task.run(getApplication())); }
      catch (Exception error) { status.postValue("Chưa hoàn tất: " + error.getMessage() + "\nThư viện offline vẫn được giữ."); }
      finally { busy.postValue(false); }
    });
  }
  @Override protected void onCleared() { io.shutdown(); }
}
