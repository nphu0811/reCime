package com.example.recime.AIservice;

import android.content.Context;
import android.graphics.*;
import java.io.*;
import java.util.concurrent.TimeUnit;
import okhttp3.*;
import org.json.*;

/** Independent backend adapter. Optional OpenAI calls require explicit user action. */
public final class LocalAiClient {
  private final String base;
  private final Context context;
  private final OkHttpClient http =
      new OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
          .connectTimeout(8, TimeUnit.SECONDS).callTimeout(65, TimeUnit.SECONDS).build();
  private final com.example.recime.helper.SessionStore session;

  public LocalAiClient(Context context) {
    this.context = context.getApplicationContext();
    base = AccountClient.endpoint(context);
    session = new com.example.recime.helper.SessionStore(context);
    HttpUrl parsed = HttpUrl.parse(base);
    if (parsed == null || parsed.username().length() > 0 || parsed.password().length() > 0)
      throw new IllegalArgumentException("Địa chỉ AI server không hợp lệ");
  }

  private JSONObject request(String path, RequestBody body) throws Exception {
    Request.Builder request = new Request.Builder().url(base + path);
    String token = session.token(base);
    if (!token.isEmpty()) request.header("Authorization", "Bearer " + token);
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
  public JSONObject extractText(String text) throws Exception {
    return request("/v1/import/ai-text", RequestBody.create(new JSONObject().put("text", text).toString(), MediaType.get("application/json; charset=utf-8")));
  }

  public JSONObject tiktok(String url) throws Exception {
    return request(
        "/import/tiktok",
        RequestBody.create(
            new JSONObject().put("url", url).toString(),
            MediaType.get("application/json; charset=utf-8")));
  }

  public JSONObject estimateNutrition(String ingredients,double servings) throws Exception {
    JSONObject json=new JSONObject();
    json.put("ingredients",ingredients);json.put("servings",servings);
    return request("/nutrition/estimate",
      RequestBody.create(json.toString(),MediaType.get("application/json; charset=utf-8")));
  }

  public JSONObject ask(com.example.recime.model.Recipe recipe,String question) throws Exception {
    JSONObject json=new JSONObject();
    json.put("title",recipe.title);
    json.put("ingredients",recipe.ingredients);
    json.put("steps",recipe.steps);
    json.put("question",question);
    return request("/assistant/ask",
        RequestBody.create(json.toString(),MediaType.get("application/json; charset=utf-8")));
  }

  public JSONObject image(File file) throws Exception {
    return uploadImage(file, "/analyze/image", 1400);
  }

  public JSONObject extractImage(File file) throws Exception {
    return uploadImage(file, "/v1/import/ai-image", 2400);
  }

  private JSONObject uploadImage(File file, String path, int maxSide) throws Exception {
    Bitmap bitmap = com.example.recime.helper.ImageHelper.readBitmap(context, file.getPath(), maxSide);
    if (bitmap == null) throw new IOException("Không đọc được ảnh");
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try {
      // Flatten transparent text pages on white; JPEG strips EXIF and location.
      Bitmap flattened = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.RGB_565);
      try {
        Canvas canvas = new Canvas(flattened);
        canvas.drawColor(Color.WHITE);
        canvas.drawBitmap(bitmap, 0, 0, null);
        if (!flattened.compress(Bitmap.CompressFormat.JPEG, 90, bytes)) throw new IOException("Không mã hóa được ảnh");
      } finally { flattened.recycle(); }
    } finally { bitmap.recycle(); }
    RequestBody body =
        new MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "photo",
                "recipe.jpg",
                RequestBody.create(bytes.toByteArray(), MediaType.get("image/jpeg")))
            .build();
    return request(path, body);
  }
}
