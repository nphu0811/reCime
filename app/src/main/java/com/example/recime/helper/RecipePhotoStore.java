package com.example.recime.helper;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.net.Uri;
import java.io.*;
import java.util.*;
import com.example.recime.model.Recipe;

/** Owned copies avoid depending on the lifetime of a Photo Picker URI grant. */
public final class RecipePhotoStore {
  public static final int MAX_PHOTOS = 5;
  public static final int MAX_BYTES = 8 * 1024 * 1024;
  private RecipePhotoStore() {}

  public static List<String> copy(Context context, List<Uri> uris) throws IOException {
    if (uris.isEmpty() || uris.size() > MAX_PHOTOS)
      throw new IOException("Chọn từ 1 đến 5 ảnh");
    File dir = new File(context.getFilesDir(), "photos");
    if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Không tạo được thư mục ảnh");
    List<String> copied = new ArrayList<>();
    try {
      for (Uri uri : uris) {
        File file = File.createTempFile("editor_", ".img", dir);
        copied.add(file.getAbsolutePath());
        try (InputStream in = context.getContentResolver().openInputStream(uri);
             OutputStream out = new FileOutputStream(file)) {
          if (in == null) throw new IOException("Không mở được ảnh đã chọn");
          byte[] buffer = new byte[8192];
          int count, total = 0;
          while ((count = in.read(buffer)) != -1) {
            total += count;
            if (total > MAX_BYTES) throw new IOException("Mỗi ảnh tối đa 8 MB");
            out.write(buffer, 0, count);
          }
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0)
          throw new IOException("Tệp được chọn không phải ảnh đọc được");
        bounds.inSampleSize = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / bounds.inSampleSize > 1024)
          bounds.inSampleSize *= 2;
        bounds.inJustDecodeBounds = false;
        android.graphics.Bitmap decoded = BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        if (decoded == null) throw new IOException("Ảnh bị hỏng hoặc không hỗ trợ");
        decoded.recycle();
      }
      return copied;
    } catch (Exception error) {
      for (String path : copied) deleteOwned(context, path);
      if (error instanceof IOException) throw (IOException) error;
      throw new IOException("Không đọc được ảnh đã chọn", error);
    }
  }

  public static void deleteOwned(Context context, String path) {
    try {
      File file = new File(path).getCanonicalFile();
      File dir = new File(context.getFilesDir(), "photos").getCanonicalFile();
      if (dir.equals(file.getParentFile()) && file.getName().startsWith("editor_")) file.delete();
    } catch (IOException ignored) { /* A failed cleanup must not delete another file. */ }
  }

  /** Only unreferenced copies created by this editor can be pruned after a DB commit. */
  public static void pruneRemoved(Context context, List<String> oldPaths, List<Recipe> savedRecipes) {
    Set<String> references = new HashSet<>();
    for (Recipe recipe : savedRecipes) references.addAll(recipe.photoPaths());
    for (String path : oldPaths) if (!references.contains(path)) deleteOwned(context, path);
  }
}
