package com.example.recime.importing;

import android.content.Context;
import android.graphics.*;
import java.io.*;
import java.util.concurrent.TimeUnit;
import okhttp3.*;
import org.json.*;

/** Real adapter to the user's own laptop. No external key or upload to a vendor. */
public final class LocalAiClient {
  private final String base;
  private final OkHttpClient http =
      new OkHttpClient.Builder().callTimeout(180, TimeUnit.SECONDS).build();

  public LocalAiClient(Context context) {
    base =
        context
            .getSharedPreferences("ai", 0)
            .getString("endpoint", "http://127.0.0.1:8765")
            .replaceAll("/+$", "");
    HttpUrl parsed = HttpUrl.parse(base);
    if (parsed == null || parsed.username().length() > 0 || parsed.password().length() > 0)
      throw new IllegalArgumentException("Địa chỉ AI server không hợp lệ");
  }

  private JSONObject request(String path, RequestBody body) throws Exception {
    Request.Builder request = new Request.Builder().url(base + path);
    if (body != null) request.post(body);
    try (Response response = http.newCall(request.build()).execute()) {
      if (response.body() == null) throw new IOException("AI server không trả dữ liệu");
      try (InputStream in = response.body().byteStream();
          ByteArrayOutputStream out = new ByteArrayOutputStream()) {
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) != -1) {
          if (out.size() + n > 2 * 1024 * 1024) throw new IOException("Kết quả quá lớn");
          out.write(buffer, 0, n);
        }
        JSONObject data = new JSONObject(out.toString("UTF-8"));
        if (!response.isSuccessful())
          throw new IOException(data.optString("detail", "HTTP " + response.code()));
        return data;
      }
    }
  }

  public JSONObject health() throws Exception {
    return request("/health", null);
  }

  public JSONObject text(String text) throws Exception {
    return request(
        "/analyze/text",
        RequestBody.create(
            new JSONObject().put("text", text).toString(),
            MediaType.get("application/json; charset=utf-8")));
  }

  public JSONObject tiktok(String url) throws Exception {
    return request(
        "/import/tiktok",
        RequestBody.create(
            new JSONObject().put("url", url).toString(),
            MediaType.get("application/json; charset=utf-8")));
  }

  public JSONObject image(File file) throws Exception {
    BitmapFactory.Options options = new BitmapFactory.Options();
    options.inJustDecodeBounds = true;
    BitmapFactory.decodeFile(file.getPath(), options);
    if (options.outWidth <= 0 || options.outHeight <= 0) throw new IOException("Ảnh không hợp lệ");
    options.inSampleSize = 1;
    while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 1400)
      options.inSampleSize *= 2;
    options.inJustDecodeBounds = false;
    Bitmap bitmap = BitmapFactory.decodeFile(file.getPath(), options);
    if (bitmap == null) throw new IOException("Không đọc được ảnh");
    int orientation =
        new android.media.ExifInterface(file.getPath())
            .getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1);
    int degrees = orientation == 6 ? 90 : orientation == 3 ? 180 : orientation == 8 ? 270 : 0;
    if (degrees != 0) {
      Matrix matrix = new Matrix();
      matrix.postRotate(degrees);
      Bitmap rotated =
          Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
      if (rotated != bitmap) bitmap.recycle();
      bitmap = rotated;
    }
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, bytes);
    bitmap.recycle();
    RequestBody body =
        new MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "photo",
                "dish.jpg",
                RequestBody.create(bytes.toByteArray(), MediaType.get("image/jpeg")))
            .build();
    return request("/analyze/image", body);
  }
}
