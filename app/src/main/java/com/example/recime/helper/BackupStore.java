package com.example.recime.helper;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.example.recime.sql.LabDatabase;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.json.*;

/** Portable checksummed backup. Restore is an additive transaction, never a database reset. */
public final class BackupStore {
  public static final int MAX_BYTES = 32 * 1024 * 1024;
  private static final String[] TABLES = {"recipes", "cookbooks", "cookbook_members", "meals", "meal_extras", "groceries", "plan_groceries", "catalog_tombstones"};

  public static JSONObject export(Context context, LabDatabase helper) throws Exception {
    SQLiteDatabase db = helper.getWritableDatabase();
    JSONObject content = new JSONObject(), tables = new JSONObject(), photos = new JSONObject();
    db.beginTransaction();
    try {
      for (String table : TABLES) {
        JSONArray rows = new JSONArray();
        try (Cursor c = db.rawQuery("SELECT * FROM " + table, null)) {
          while (c.moveToNext()) {
            JSONObject row = new JSONObject();
            for (int i = 0; i < c.getColumnCount(); i++) {
              Object value = c.isNull(i) ? JSONObject.NULL : c.getType(i) == Cursor.FIELD_TYPE_INTEGER
                  ? c.getLong(i) : c.getType(i) == Cursor.FIELD_TYPE_FLOAT ? c.getDouble(i) : c.getString(i);
              row.put(c.getColumnName(i), value);
            }
            if (table.equals("recipes")) {
              row.put("image", packPhoto(context, row.optString("image"), photos));
              JSONArray paths = new JSONArray(row.optString("imagesJson", "[]"));
              for (int i = 0; i < paths.length(); i++) paths.put(i, packPhoto(context, paths.getString(i), photos));
              row.put("imagesJson", paths.toString());
            }
            rows.put(row);
          }
        }
        tables.put(table, rows);
      }
      db.setTransactionSuccessful();
    } finally { db.endTransaction(); }
    JSONObject prefs = new JSONObject();
    for (String group : new String[] {"planner", "units"}) {
      JSONObject values = new JSONObject();
      for (Map.Entry<String, ?> entry : context.getSharedPreferences(group, 0).getAll().entrySet())
        if (entry.getValue() instanceof Number || entry.getValue() instanceof Boolean || entry.getValue() instanceof String)
          values.put(entry.getKey(), entry.getValue());
      prefs.put(group, values);
    }
    content.put("tables", tables).put("photos", photos).put("preferences", prefs);
    String payload = content.toString();
    JSONObject envelope = new JSONObject().put("format", "recime-backup").put("version", 1)
        .put("schemaVersion", LabDatabase.DATABASE_VERSION).put("backupId", UUID.randomUUID().toString())
        .put("createdAt", System.currentTimeMillis()).put("content", payload).put("sha256", digest(payload.getBytes(StandardCharsets.UTF_8)));
    if (envelope.toString().getBytes(StandardCharsets.UTF_8).length > MAX_BYTES)
      throw new IOException("Backup vượt 32MB. Hãy xuất từng công thức hoặc giảm ảnh trước.");
    return envelope;
  }

  private static String packPhoto(Context context, String path, JSONObject photos) throws Exception {
    if (path.isEmpty() || path.startsWith("file:///android_asset/catalog/")) return path;
    File file = new File(path);
    File photoRoot = new File(context.getFilesDir(), "photos").getCanonicalFile();
    if (!file.getCanonicalPath().startsWith(photoRoot.getPath() + File.separator)) return path;
    if (!file.isFile()) return "";
    byte[] bytes;
    try (InputStream in = new FileInputStream(file)) { bytes = read(in, 8 * 1024 * 1024); }
    String key = digest(bytes);
    if (!photos.has(key)) photos.put(key, android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP));
    if (photos.toString().length() > 24 * 1024 * 1024) throw new IOException("Ảnh backup vượt giới hạn");
    return "backup:" + key;
  }

  public static byte[] read(InputStream in, int maximum) throws IOException {
    if (in == null) throw new IOException("Không đọc được tệp");
    ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int n;
    while ((n = in.read(buffer)) != -1) {
      if (out.size() + n > maximum) throw new IOException("Tệp vượt giới hạn kích thước");
      out.write(buffer, 0, n);
    }
    return out.toByteArray();
  }

  public static String digest(byte[] bytes) throws Exception {
    StringBuilder b = new StringBuilder();
    for (byte value : MessageDigest.getInstance("SHA-256").digest(bytes)) b.append(String.format(Locale.ROOT, "%02x", value & 255));
    return b.toString();
  }

  public static JSONObject validate(JSONObject envelope) throws Exception {
    if (!envelope.optString("format").equals("recime-backup") || envelope.optInt("version") != 1
        || envelope.optInt("schemaVersion") > LabDatabase.DATABASE_VERSION || envelope.optInt("schemaVersion") < 5)
      throw new IOException("Định dạng hoặc phiên bản backup không hỗ trợ");
    UUID.fromString(envelope.getString("backupId"));
    String content = envelope.getString("content");
    if (content.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES
        || !digest(content.getBytes(StandardCharsets.UTF_8)).equals(envelope.getString("sha256")))
      throw new IOException("Backup hỏng hoặc checksum không khớp");
    JSONObject payload = new JSONObject(content);
    JSONObject tables = payload.getJSONObject("tables");
    for (String table : TABLES) if (tables.getJSONArray(table).length() > 50000)
      throw new IOException("Quá nhiều bản ghi");
    return payload;
  }

  public static int restore(Context context, LabDatabase helper, JSONObject envelope) throws Exception {
    JSONObject payload = validate(envelope), tables = payload.getJSONObject("tables");
    SQLiteDatabase db = helper.getWritableDatabase();
    String backupId = envelope.getString("backupId");
    List<File> created = new ArrayList<>();
    Map<String, String> photos = new HashMap<>();
    Map<Long, Long> recipes = new HashMap<>(), books = new HashMap<>();
    boolean committed = false; int inserted = 0;
    db.beginTransaction();
    try {
      try (Cursor c = db.rawQuery("SELECT 1 FROM backup_restores WHERE backupId=?", new String[] {backupId})) {
        if (c.moveToFirst()) { db.setTransactionSuccessful(); return 0; }
      }
      JSONObject blobs = payload.getJSONObject("photos");
      File dir = new File(context.getFilesDir(), "photos");
      if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Không tạo được thư mục ảnh");
      Iterator<String> keys = blobs.keys(); int total = 0;
      while (keys.hasNext()) {
        String hash = keys.next();
        byte[] bytes = android.util.Base64.decode(blobs.getString(hash), android.util.Base64.DEFAULT);
        total += bytes.length;
        if (bytes.length > 8 * 1024 * 1024 || total > 24 * 1024 * 1024 || !digest(bytes).equals(hash))
          throw new IOException("Ảnh backup hỏng hoặc quá lớn");
        android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new IOException("Ảnh backup không hợp lệ");
        File target = File.createTempFile("restore_", ".img", dir); created.add(target);
        try (OutputStream out = new FileOutputStream(target)) { out.write(bytes); }
        photos.put("backup:" + hash, target.getAbsolutePath());
      }
      JSONArray rows = tables.getJSONArray("recipes");
      for (int i = 0; i < rows.length(); i++) {
        JSONObject row = rows.getJSONObject(i); long old = row.getLong("id");
        String key = row.isNull("catalogKey") ? null : row.optString("catalogKey");
        Long existing = key == null ? null : lookup(db, "SELECT id FROM recipes WHERE catalogKey=?", key);
        if (existing != null) { recipes.put(old, existing); continue; }
        ContentValues values = values(db, "recipes", row); values.remove("id");
        values.put("image", unpackPhoto(row.optString("image"), photos));
        JSONArray images = new JSONArray(row.optString("imagesJson", "[]"));
        if (images.length() > 5) throw new IOException("Công thức vượt 5 ảnh");
        for (int j = 0; j < images.length(); j++) images.put(j, unpackPhoto(images.getString(j), photos));
        values.put("imagesJson", images.toString());
        recipes.put(old, db.insertOrThrow("recipes", null, values)); inserted++;
      }
      rows = tables.getJSONArray("cookbooks");
      for (int i = 0; i < rows.length(); i++) {
        JSONObject row = rows.getJSONObject(i);
        Long existing = lookup(db, "SELECT id FROM cookbooks WHERE name=? COLLATE NOCASE", row.getString("name"));
        ContentValues values = values(db, "cookbooks", row); values.remove("id");
        // Cookbook covers are not currently authored by the app; do not restore arbitrary paths.
        values.put("cover", "");
        books.put(row.getLong("id"), existing != null ? existing : db.insertOrThrow("cookbooks", null, values));
      }
      rows = tables.getJSONArray("cookbook_members");
      for (int i = 0; i < rows.length(); i++) {
        JSONObject row = rows.getJSONObject(i); ContentValues v = new ContentValues();
        v.put("cookbookId", required(books, row.getLong("cookbookId"))); v.put("recipeId", required(recipes, row.getLong("recipeId")));
        db.insertWithOnConflict("cookbook_members", null, v, SQLiteDatabase.CONFLICT_IGNORE);
      }
      for (String table : new String[] {"meals", "meal_extras"}) {
        rows = tables.getJSONArray(table);
        for (int i = 0; i < rows.length(); i++) {
          JSONObject row = rows.getJSONObject(i); ContentValues v = new ContentValues();
          v.put("recipeId", required(recipes, row.getLong("recipeId")));
          v.put("day", row.getString("day")); v.put("type", row.getString("type"));
          v.put("servings", row.optDouble("servings", 0));
          if (v.getAsDouble("servings") <= 0) {
            try (Cursor c = db.rawQuery("SELECT servings FROM recipes WHERE id=?", new String[] {v.getAsString("recipeId")})) { c.moveToFirst(); v.put("servings", c.getDouble(0)); }
          }
          v.put("position", row.optInt("position", 0)); db.insertOrThrow("meal_extras", null, v);
        }
      }
      rows = tables.getJSONArray("groceries");
      for (int i = 0; i < rows.length(); i++) { ContentValues v = values(db, "groceries", rows.getJSONObject(i)); v.remove("id"); db.insertOrThrow("groceries", null, v); }
      rows = tables.getJSONArray("catalog_tombstones");
      for (int i = 0; i < rows.length(); i++) db.insertWithOnConflict("catalog_tombstones", null, values(db, "catalog_tombstones", rows.getJSONObject(i)), SQLiteDatabase.CONFLICT_IGNORE);
      helper.dao().syncPlanGroceries();
      rows = tables.getJSONArray("plan_groceries");
      for (int i = 0; i < rows.length(); i++) {
        JSONObject row = rows.getJSONObject(i);
        if (row.optInt("checked") != 0) { ContentValues v = new ContentValues(); v.put("checked", 1); db.update("plan_groceries", v, "itemKey=?", new String[] {row.getString("itemKey")}); }
      }
      ContentValues marker = new ContentValues(); marker.put("backupId", backupId); marker.put("importedAt", System.currentTimeMillis()); db.insertOrThrow("backup_restores", null, marker);
      try (Cursor c = db.rawQuery("PRAGMA foreign_key_check", null)) { if (c.moveToFirst()) throw new IOException("Backup có liên kết dữ liệu không hợp lệ"); }
      db.setTransactionSuccessful(); committed = true;
    } finally {
      db.endTransaction();
      if (!committed) for (File file : created) file.delete();
    }
    helper.dao().refresh();
    // Preferences are offered separately in Settings; restore never overwrites this device's choices.
    return inserted;
  }

  private static String unpackPhoto(String path, Map<String, String> photos) throws IOException {
    if (path.startsWith("backup:")) { String value = photos.get(path); if (value == null) throw new IOException("Backup thiếu ảnh"); return value; }
    if (path.startsWith("file:///android_asset/catalog/") && !path.contains("..")) return path;
    return "";
  }
  private static long required(Map<Long, Long> mapping, long old) throws IOException { Long value = mapping.get(old); if (value == null) throw new IOException("Backup thiếu công thức/cookbook liên kết"); return value; }
  private static Long lookup(SQLiteDatabase db, String sql, String arg) { try (Cursor c = db.rawQuery(sql, new String[] {arg})) { return c.moveToFirst() ? c.getLong(0) : null; } }
  private static ContentValues values(SQLiteDatabase db, String table, JSONObject row) throws Exception {
    Set<String> allowed = new HashSet<>();
    try (Cursor c = db.rawQuery("PRAGMA table_info(" + table + ")", null)) { while (c.moveToNext()) allowed.add(c.getString(1)); }
    ContentValues values = new ContentValues(); Iterator<String> keys = row.keys();
    while (keys.hasNext()) {
      String key = keys.next(); if (!allowed.contains(key)) throw new IOException("Cột backup không hỗ trợ");
      Object value = row.get(key);
      if (value == JSONObject.NULL) values.putNull(key);
      else if (value instanceof Number) { double n = ((Number) value).doubleValue(); if (!Double.isFinite(n)) throw new IOException("Số không hợp lệ"); if (value instanceof Integer || value instanceof Long) values.put(key, ((Number) value).longValue()); else values.put(key, n); }
      else if (value instanceof String) values.put(key, (String) value);
      else throw new IOException("Dữ liệu backup không hợp lệ");
    }
    return values;
  }
  private BackupStore() {}
}
