package com.example.recime.viewmodel;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.io.*;
import java.util.concurrent.*;

/** The picked URI is copied and recognized off main; no old view owns the result. */
public final class ImportImageState extends AndroidViewModel {
  public static final class Result {
    public long id;
    public boolean loading, dish;
    public String image = "", text = "", error;
  }
  public final MutableLiveData<Result> result = new MutableLiveData<>();
  private final Handler main = new Handler(Looper.getMainLooper());
  private long generation;
  private volatile boolean closed;
  private final java.util.Set<String> ownedPaths = java.util.concurrent.ConcurrentHashMap.newKeySet();
  public ImportImageState(Application app) { super(app); }
  public boolean isLoading() { return result.getValue() != null && result.getValue().loading; }
  public void read(Uri uri, boolean dish, ExecutorService executor) {
    if (isLoading()) return;
    Result initial = new Result();
    initial.id = ++generation; initial.loading = true; initial.dish = dish;
    result.setValue(initial);
    executor.execute(() -> {
      Result next = new Result(); next.id = initial.id; next.dish = dish;
      File file = null;
      Bitmap bitmap = null;
      TextRecognizer recognizer = null;
      try {
        File dir = new File(getApplication().getFilesDir(), "photos");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Không tạo được thư mục ảnh");
        file = File.createTempFile("recipe_", ".img", dir);
        ownedPaths.add(file.getAbsolutePath());
        try (InputStream in = getApplication().getContentResolver().openInputStream(uri);
             OutputStream out = new FileOutputStream(file)) {
          if (in == null) throw new IOException("Không có quyền đọc ảnh; hãy chọn lại");
          byte[] buffer = new byte[8192]; int count, total = 0;
          while ((count = in.read(buffer)) != -1) {
            total += count;
            if (total > 8 * 1024 * 1024) throw new IOException("Ảnh tối đa 8MB");
            out.write(buffer, 0, count);
          }
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        if (options.outWidth <= 0 || options.outHeight <= 0) throw new IOException("Ảnh không hợp lệ");
        options.inSampleSize = 1;
        while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 2400)
          options.inSampleSize *= 2;
        if (!dish) {
          options.inJustDecodeBounds = false;
          bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
          if (bitmap == null) throw new IOException("Không giải mã được ảnh");
          int orientation = new android.media.ExifInterface(file.getAbsolutePath())
              .getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1);
          int rotation = orientation == 6 ? 90 : orientation == 3 ? 180 : orientation == 8 ? 270 : 0;
          recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
          // The recognizer may finish after timeout; release its bitmap only when complete.
          Bitmap owned = bitmap;
          TextRecognizer ownedRecognizer = recognizer;
          com.google.android.gms.tasks.Task<com.google.mlkit.vision.text.Text> task = recognizer.process(InputImage.fromBitmap(bitmap, rotation));
          task.addOnCompleteListener(executor, ignored -> { ownedRecognizer.close(); owned.recycle(); });
          recognizer = null; bitmap = null;
          next.text = Tasks.await(task, 60, TimeUnit.SECONDS).getText();
        }
        next.image = file.getAbsolutePath();
      } catch (Exception error) {
        if (file != null) file.delete();
        next.error = error instanceof TimeoutException ? "OCR quá thời gian; chọn ảnh rõ hơn rồi thử lại"
            : "Không đọc được ảnh; kiểm tra định dạng và quyền truy cập";
      } finally {
        if (recognizer != null) recognizer.close();
        if (bitmap != null) bitmap.recycle();
      }
      main.post(() -> { if (!closed && generation == next.id) result.setValue(next); else cleanup(next.image); });
    });
  }
  private void cleanup(String path) {
    if (path == null || path.isEmpty()) return;
    com.example.recime.sql.RecipeRepository repo = com.example.recime.sql.RecipeRepository.get(getApplication());
    repo.io.execute(() -> {
      for (com.example.recime.model.Recipe recipe : repo.dao.recipeSnapshot()) if (recipe.photoPaths().contains(path)) return;
      File file = new File(path);
      if (file.getName().startsWith("recipe_") && file.getParentFile().equals(new File(getApplication().getFilesDir(), "photos"))) file.delete();
    });
  }
  @Override protected void onCleared() { closed = true; for(String path:ownedPaths)cleanup(path); }
}
