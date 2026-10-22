package com.example.recime.helper;


import java.net.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import okhttp3.*;
import org.json.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

public final class PublicWebExtractor implements ExtractionAdapter {
  public static void validate(String value) throws Exception {
    URI u = new URI(value);
    if (!"https".equalsIgnoreCase(u.getScheme())
        || u.getHost() == null
        || u.getUserInfo() != null
        || (u.getPort() != -1 && u.getPort() != 443))
      throw new IllegalArgumentException("Chỉ nhận URL HTTPS công khai");
    String host = u.getHost().toLowerCase(Locale.ROOT);
    if (host.matches(
        ".*(instagram\\.com|tiktok\\.com|facebook\\.com|youtube\\.com|youtu\\.be|pinterest\\.com)$"))
      throw new IllegalArgumentException(
          "Chưa hỗ trợ video social. Hãy dán caption hoặc chọn screenshot.");
    for (InetAddress a : InetAddress.getAllByName(host)) checkAddress(a);
  }

  private static void checkAddress(InetAddress a) throws UnknownHostException {
    byte[] b = a.getAddress();
    if (a.isAnyLocalAddress()
        || a.isLoopbackAddress()
        || a.isLinkLocalAddress()
        || a.isSiteLocalAddress()
        || a.isMulticastAddress()
        || (b.length == 16 && (b[0] & 0xfe) == 0xfc))
      throw new UnknownHostException("Không nhận địa chỉ mạng nội bộ");
  }

  @Override
  public String extract(String input) throws Exception {
    validate(input);
    OkHttpClient client =
        new OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .callTimeout(20, TimeUnit.SECONDS)
            .dns(
                host -> {
                  List<InetAddress> list = Arrays.asList(InetAddress.getAllByName(host));
                  for (InetAddress a : list) checkAddress(a);
                  return list;
                })
            .build();
    String url = input;
    for (int redirects = 0; redirects <= 3; redirects++) {
      validate(url);
      try (Response response =
          client
              .newCall(
                  new Request.Builder()
                      .url(url)
                      .header("User-Agent", "MyRecipeLab/1.0 public Recipe JSON-LD reader")
                      .build())
              .execute()) {
        if (response.code() >= 300 && response.code() < 400) {
          String location = response.header("Location");
          if (location == null) break;
          url = new URI(url).resolve(location).toString();
          continue;
        }
        if (!response.isSuccessful() || response.body() == null)
          throw new IllegalArgumentException(
              "Không đọc được trang (HTTP "
                  + response.code()
                  + "). Hãy dán văn bản có quyền sử dụng.");
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        try (java.io.InputStream stream = response.body().byteStream()) {
          while ((n = stream.read(buffer)) != -1) {
            if (bytes.size() + n > 2 * 1024 * 1024)
              throw new IllegalArgumentException("Trang vượt giới hạn 2MB");
            bytes.write(buffer, 0, n);
          }
        }
        String html = bytes.toString("UTF-8");
        for (Element e : Jsoup.parse(html).select("script[type=application/ld+json]")) {
          try {
            JSONObject r = find(new JSONTokener(e.data()).nextValue());
            if (r != null) return toText(r);
          } catch (JSONException ignored) {
          }
        }
        throw new IllegalArgumentException(
            "Không có Recipe JSON-LD hỗ trợ. Hãy dán caption/văn bản rồi kiểm tra lại.");
      }
    }
    throw new IllegalArgumentException("Quá nhiều chuyển hướng hoặc trang không hợp lệ");
  }

  static JSONObject find(Object o) throws JSONException {
    if (o instanceof JSONArray) {
      JSONArray a = (JSONArray) o;
      for (int i = 0; i < a.length(); i++) {
        JSONObject found = find(a.get(i));
        if (found != null) return found;
      }
    }
    if (o instanceof JSONObject) {
      JSONObject j = (JSONObject) o;
      Object type = j.opt("@type");
      if ("Recipe".equals(type)
          || (type instanceof JSONArray && type.toString().contains("\"Recipe\""))) return j;
      if (j.has("@graph")) return find(j.get("@graph"));
    }
    return null;
  }

  static String toText(JSONObject r) throws JSONException {
    StringBuilder b =
        new StringBuilder(Jsoup.parse(r.optString("name", "Công thức nhập web")).text())
            .append("\nIngredients\n");
    JSONArray a = r.optJSONArray("recipeIngredient");
    if (a != null)
      for (int i = 0; i < a.length(); i++)
        b.append(Jsoup.parse(a.getString(i)).text()).append('\n');
    b.append("Instructions\n");
    instructions(r.opt("recipeInstructions"), b);
    return b.toString();
  }

  static void instructions(Object o, StringBuilder b) throws JSONException {
    if (o instanceof JSONArray) {
      JSONArray a = (JSONArray) o;
      for (int i = 0; i < a.length(); i++) instructions(a.get(i), b);
    } else if (o instanceof JSONObject) {
      JSONObject j = (JSONObject) o;
      if (j.has("text")) b.append(Jsoup.parse(j.getString("text")).text()).append('\n');
      else instructions(j.opt("itemListElement"), b);
    } else if (o instanceof String) b.append(Jsoup.parse((String) o).text()).append('\n');
  }
}
