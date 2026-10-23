package com.example.recime.viewmodel;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.AndroidViewModel;
import android.app.Application;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import com.example.recime.helper.RecipePhotoStore;
import java.util.*;
import java.util.concurrent.*;

/** Fragment-scoped save outcome; contains no Activity or View references. */
public class EditorSaveState extends AndroidViewModel {
  public EditorSaveState(Application application) { super(application); }
  public volatile boolean saving;
  public final MutableLiveData<String> outcome = new MutableLiveData<>();
  public final MutableLiveData<Integer> photoRevision = new MutableLiveData<>(0);
  public final MutableLiveData<String> photoError = new MutableLiveData<>();
  public final ArrayList<String> photos = new ArrayList<>();
  public boolean photosInitialized, importingPhotos;
  public int replacementIndex = -1;
  private final Set<String> staged = new HashSet<>();
  private final ExecutorService photoIo = Executors.newSingleThreadExecutor();
  private final Handler main = new Handler(Looper.getMainLooper());
  private boolean closed;

  public synchronized ArrayList<String> stagedPaths() { return new ArrayList<>(staged); }
  public synchronized void restoreStaged(List<String> paths) { staged.addAll(paths); }
  public synchronized void photosSaved(List<String> savedPaths) {
    staged.removeAll(savedPaths);
    if (closed) cleanup();
  }
  public synchronized void photoSaveFailed() { if (closed) cleanup(); }

  public void importPhotos(List<Uri> uris, int replace) {
    if (uris.isEmpty() || saving || importingPhotos) return;
    if (replace < 0 && photos.size() + uris.size() > RecipePhotoStore.MAX_PHOTOS) {
      photoError.setValue("Tối đa 5 ảnh; hãy chọn ít ảnh hơn hoặc xóa ảnh cũ.");
      return;
    }
    importingPhotos = true;
    changed();
    photoIo.execute(() -> {
      try {
        List<String> copied = RecipePhotoStore.copy(getApplication(), uris);
        main.post(() -> {
          importingPhotos = false;
          if (closed) {
            for (String path : copied) RecipePhotoStore.deleteOwned(getApplication(), path);
            return;
          }
          synchronized (this) { staged.addAll(copied); }
          if (replace >= 0 && replace < photos.size()) photos.set(replace, copied.get(0));
          else photos.addAll(copied);
          photoError.setValue("");
          changed();
        });
      } catch (Exception error) {
        main.post(() -> {
          importingPhotos = false;
          if (!closed) { photoError.setValue(error.getMessage()); changed(); }
        });
      }
    });
  }

  public void removePhoto(int index) {
    if (saving || importingPhotos) return;
    photos.remove(index);
    changed();
  }

  public void changed() { photoRevision.setValue(photoRevision.getValue() + 1); }

  private void cleanup() {
    for (String path : staged) RecipePhotoStore.deleteOwned(getApplication(), path);
    staged.clear();
  }

  @Override protected synchronized void onCleared() {
    closed = true;
    if (!saving) cleanup();
    photoIo.shutdown();
  }
}
