package com.example.recime.helper;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.LruCache;
import android.widget.ImageView;
import com.example.recime.R;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Đọc ảnh bằng Android SDK, không dùng Glide/Picasso. */
public final class ImageHelper {
  private static final ExecutorService imageThread = Executors.newFixedThreadPool(2);
  private static final LruCache<String, Bitmap> cache =
      new LruCache<String, Bitmap>(8 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap bitmap) {
          return bitmap.getByteCount();
        }
      };

  private ImageHelper() {}

  public static void load(ImageView image, String path) {
    image.setTag(path);
    image.setImageResource(R.drawable.food_art);
    if (path == null || path.isEmpty()) return;
    Bitmap saved = cache.get(path);
    if (saved != null) {
      image.setImageBitmap(saved);
      return;
    }
    Context context = image.getContext().getApplicationContext();
    // Đọc file/mạng ngoài luồng UI để ListView/GridView không đứng khi cuộn.
    imageThread.execute(
        () -> {
          Bitmap bitmap;
          try {
            bitmap = readBitmap(context, path);
          } catch (Exception error) {
            bitmap = null;
          }
          if (bitmap != null) cache.put(path, bitmap);
          final Bitmap result = bitmap;
          image.post(
              () -> {
                // convertView có thể đã được bind sang món khác khi tác vụ ảnh kết thúc.
                if (path.equals(image.getTag())) {
                  if (result != null) image.setImageBitmap(result);
                  else image.setImageResource(R.drawable.food_art);
                }
              });
        });
  }

  static Bitmap readBitmap(Context context, String path) throws IOException {
    return readBitmap(context, path, 800);
  }

  /** Bounded decode with all EXIF orientations, shared by OCR and explicit uploads. */
  public static Bitmap readBitmap(Context context, String path, int maxSide) throws IOException {
    if (maxSide < 100 || maxSide > 2400) throw new IllegalArgumentException("Invalid image size");
    byte[] bytes;
    try (InputStream input = open(context, path);
        ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      if (input == null) throw new IOException("Không đọc được ảnh");
      byte[] buffer = new byte[8192];
      int count;
      while ((count = input.read(buffer)) != -1) {
        if (output.size() + count > 8 * 1024 * 1024) throw new IOException("Ảnh quá lớn");
        output.write(buffer, 0, count);
      }
      bytes = output.toByteArray();
    }
    BitmapFactory.Options options = new BitmapFactory.Options();
    options.inJustDecodeBounds = true;
    BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
    if (options.outWidth <= 0 || options.outHeight <= 0) throw new IOException("Ảnh không hợp lệ");
    options.inSampleSize = 1;
    while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > maxSide)
      options.inSampleSize *= 2;
    options.inJustDecodeBounds = false;
    Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
    if (bitmap == null) return null;
    // Ảnh camera có thể lưu hướng xoay trong EXIF thay vì xoay các pixel.
    int orientation = 1;
    try {
      android.media.ExifInterface exif =
          new android.media.ExifInterface(new ByteArrayInputStream(bytes));
      orientation = exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1);
    } catch (IOException noExif) {
      return bitmap;
    }
    android.graphics.Matrix matrix = new android.graphics.Matrix();
    switch (orientation) {
      case 2:
        matrix.setScale(-1, 1);
        break;
      case 3:
        matrix.setRotate(180);
        break;
      case 4:
        matrix.setScale(1, -1);
        break;
      case 5:
        matrix.setRotate(90);
        matrix.postScale(-1, 1);
        break;
      case 6:
        matrix.setRotate(90);
        break;
      case 7:
        matrix.setRotate(-90);
        matrix.postScale(-1, 1);
        break;
      case 8:
        matrix.setRotate(-90);
        break;
      default:
        return bitmap;
    }
    Bitmap rotated =
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    if (rotated != bitmap) bitmap.recycle();
    return rotated;
  }

  private static InputStream open(Context context, String path) throws IOException {
    String assets = "file:///android_asset/";
    if (path.startsWith(assets)) return context.getAssets().open(path.substring(assets.length()));
    if (path.startsWith("content://"))
      return context.getContentResolver().openInputStream(Uri.parse(path));
    if (path.startsWith("http://") || path.startsWith("https://")) {
      HttpURLConnection connection = (HttpURLConnection) new URL(path).openConnection();
      connection.setConnectTimeout(10000);
      connection.setReadTimeout(10000);
      try {
        if (connection.getResponseCode() != 200)
          throw new IOException("Ảnh HTTP " + connection.getResponseCode());
        return new java.io.FilterInputStream(connection.getInputStream()) {
          @Override
          public void close() throws IOException {
            try {
              super.close();
            } finally {
              connection.disconnect();
            }
          }
        };
      } catch (IOException error) {
        connection.disconnect();
        throw error;
      }
    }
    return new FileInputStream(path.startsWith("file://") ? Uri.parse(path).getPath() : path);
  }
}
