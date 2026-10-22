package com.example.recime.helper;

import android.content.Context;
import com.example.recime.model.Recipe;
import com.example.recime.sql.LabDatabase;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.json.*;

/** Bundled, versioned open recipes: real Room rows, no network required at launch. */
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
    database.runInTransaction(
        () -> {
          try {
            for (int i = 0; i < recipes.length(); i++) {
              JSONObject value = recipes.getJSONObject(i);
              Recipe recipe = new Recipe();
              recipe.catalogKey = value.getString("catalogKey");
              if (database.dao().wasCatalogDeleted(recipe.catalogKey) > 0) continue;
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
              Recipe existing = database.dao().findCatalog(recipe.catalogKey);
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
