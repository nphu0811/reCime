package com.example.recime.AIservice;

import android.content.Context;
import com.example.recime.helper.*;
import java.io.*;
import java.util.concurrent.TimeUnit;
import okhttp3.*;
import org.json.*;

/** Versioned independent server contract. No token is sent across redirects or to another origin. */
public final class AccountClient {
  private final String base;
  private final SessionStore session;
  private final OkHttpClient http = new OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
      .connectTimeout(8, TimeUnit.SECONDS).callTimeout(45, TimeUnit.SECONDS).build();
  public AccountClient(Context context) {
    base = endpoint(context); session = new SessionStore(context);
  }
  public static String endpoint(Context context) {
    String value = context.getSharedPreferences("ai", 0).getString("endpoint", "http://127.0.0.1:8765").replaceAll("/+$", "");
    HttpUrl parsed = HttpUrl.parse(value);
    if (parsed == null || !parsed.username().isEmpty() || !parsed.password().isEmpty()
        || !parsed.encodedPath().equals("/") || parsed.query() != null || parsed.fragment() != null)
      throw new IllegalArgumentException("Nhập URL gốc backend, không có đường dẫn hoặc credentials");
    if (!parsed.isHttps()) {
      boolean debug = (context.getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
      String host = parsed.host();
      if (!debug || !(host.equals("127.0.0.1") || host.equals("10.0.2.2") || host.equals("localhost")))
        throw new IllegalArgumentException("Backend cần HTTPS; debug chỉ cho HTTP localhost/10.0.2.2");
    }
    return value;
  }
  public JSONObject request(String method, String path, JSONObject payload) throws Exception {
    Request.Builder request = new Request.Builder().url(base + path);
    String token = session.token(base);
    if (!token.isEmpty()) request.header("Authorization", "Bearer " + token);
    RequestBody body = payload == null ? null : RequestBody.create(payload.toString(), MediaType.get("application/json; charset=utf-8"));
    request.method(method, body);
    try (Response response = http.newCall(request.build()).execute()) {
      if (response.body() == null) throw new IOException("Backend không trả dữ liệu");
      JSONObject data;
      try (InputStream in = response.body().byteStream()) {
        data = new JSONObject(new String(BackupStore.read(in, 12 * 1024 * 1024), java.nio.charset.StandardCharsets.UTF_8));
      }
      if (!response.isSuccessful()) {
        if (response.code() == 401 && !path.startsWith("/v1/auth/")) session.clear();
        throw new IOException("HTTP " + response.code() + ": " + data.opt("detail"));
      }
      return data;
    }
  }
  public JSONObject login(String email, String password, boolean register) throws Exception {
    JSONObject result = request("POST", register ? "/v1/auth/register" : "/v1/auth/login", new JSONObject().put("email", email).put("password", password));
    JSONObject user = result.getJSONObject("user");
    session.save(result.getString("accessToken"), user.getString("id"), user.getString("email"), base);
    return user;
  }
  public void logout() throws Exception {
    try { request("POST", "/v1/auth/logout", new JSONObject()); } finally { session.clear(); }
  }
}
