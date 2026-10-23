package com.example.recime.helper;

import android.content.Context;
import android.database.Cursor;
import com.example.recime.model.Recipe;
import com.example.recime.sql.LabDatabase;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** Bundled, versioned open recipes: real SQLite rows, no network required at launch. */
public final class CatalogImporter {
  public static int install(Context context, LabDatabase database) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (InputStream in = context.getAssets().open("catalog/recipes.json")) {
      byte[] buffer = new byte[8192];
      int count;
      while ((count = in.read(buffer)) != -1) bytes.write(buffer, 0, count);
    }
    JSONArray recipes =
        new JSONObject(bytes.toString(StandardCharsets.UTF_8.name())).getJSONArray("recipes");
    int[] added = {0};

    // Pre-fetch deleted tombstones and existing catalog recipes in bulk
    // before starting the transaction to avoid thousands of individual SELECT queries.
    Set<String> deletedTombstones = new HashSet<>();
    try (Cursor cursor = database.getReadableDatabase().rawQuery("SELECT catalogKey FROM catalog_tombstones", null)) {
      while (cursor.moveToNext()) {
        String key = cursor.getString(0);
        if (key != null) deletedTombstones.add(key);
      }
    }

    Map<String, Recipe> existingCatalogMap = new HashMap<>();
    for (Recipe r : database.dao().recipeSnapshot()) {
      if (r.catalogKey != null) {
        existingCatalogMap.put(r.catalogKey, r);
      }
    }

    database.runInTransaction(
        () -> {
          try {
            for (int i = 0; i < recipes.length(); i++) {
              JSONObject value = recipes.getJSONObject(i);
              String catalogKey = value.getString("catalogKey");
              if (deletedTombstones.contains(catalogKey)) continue;

              Recipe recipe = new Recipe();
              recipe.catalogKey = catalogKey;
              recipe.title = value.getString("title");
              recipe.ingredients = value.getString("ingredients");
              recipe.steps = value.getString("steps");
              recipe.cookbook = value.getString("cookbook");
              recipe.tags = value.optString("tags");
              recipe.source = value.getString("source");
              recipe.servings = value.optDouble("servings", 1);
              recipe.servingsKnown = value.optBoolean("servingsKnown", false);
              String image = value.optString("image");
              recipe.image = image.isEmpty() ? "" : "file:///android_asset/catalog/" + image;
              recipe.attribution =
                  value.getString("authors")
                      + " · "
                      + value.getString("license")
                      + "\n"
                      + value.getString("licenseUrl")
                      + "\nLịch sử tác giả: "
                      + value.getString("history")
                      + "\n"
                      + value.getString("modifications");
              if (!image.isEmpty())
                recipe.imageAttribution =
                    value.optString("imageAttribution")
                        + " · "
                        + value.optString("imageLicense")
                        + "\n"
                        + value.optString("imageSource")
                        + "\n"
                        + value.optString("imageLicenseUrl")
                        + "\n"
                        + value.optString("imageMatch")
                        + "\n"
                        + value.optString("imageModification");

              Recipe existing = existingCatalogMap.get(catalogKey);
              if (existing != null) {
                // Catalog upgrades can fill missing photos without overwriting user edits or
                // favorites.
                if (existing.image.isEmpty() && !recipe.image.isEmpty()) {
                  existing.image = recipe.image;
                  existing.imageAttribution = recipe.imageAttribution;
                  database.dao().update(existing);
                }
                continue;
              }
              if (database.dao().insertCatalog(recipe) != -1) added[0]++;
            }
          } catch (JSONException e) {
            throw new IllegalStateException("Dataset không hợp lệ", e);
          }
        });
    return added[0];
  }
}
