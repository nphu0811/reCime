package com.example.recime.helper;

import com.example.recime.model.ParsedRecipe;
import com.example.recime.model.Recipe;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;

/** Suggests an existing personal recipe before importing a likely duplicate. */
public final class ImportDuplicateChecker {
  private ImportDuplicateChecker() {}

  public static Recipe find(List<Recipe> recipes, ParsedRecipe draft, String source) {
    if (recipes == null || draft == null) return null;
    String sourceKey = webSourceKey(source);
    String title = key(draft.title);
    String ingredients = key(draft.ingredients);
    String steps = key(draft.steps);
    for (Recipe recipe : recipes) {
      // The offline catalog is not part of a user's personal cookbook.
      if (recipe.catalogKey != null) continue;
      if (!sourceKey.isEmpty() && sourceKey.equals(webSourceKey(recipe.source))) return recipe;
      // Only flag a content match when all three substantive fields are present.
      if (!title.isEmpty() && !ingredients.isEmpty() && !steps.isEmpty()
          && title.equals(key(recipe.title))
          && ingredients.equals(key(recipe.ingredients))
          && steps.equals(key(recipe.steps))) return recipe;
    }
    return null;
  }

  private static String key(String text) {
    return text == null ? "" : Units.key(text);
  }

  /** Canonicalize public HTTPS origins, avoiding false matches on free-form labels. */
  static String webSourceKey(String source) {
    if (source == null) return "";
    try {
      URI uri = new URI(source.trim());
      if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
          || uri.getUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443))
        return "";
      String host = uri.getHost().toLowerCase(Locale.ROOT);
      String path = uri.getRawPath();
      if (path == null || path.isEmpty()) path = "/";
      if (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);
      // Keep meaningful query params: different ?recipe= IDs are not duplicates.
      String query = uri.getRawQuery();
      return host + path + (query == null || query.isEmpty() ? "" : "?" + query);
    } catch (URISyntaxException | IllegalArgumentException invalid) {
      return "";
    }
  }
}
