package com.example.recime.helper;

import com.example.recime.model.Ingredient;
import java.util.List;

/** Matches comma-separated ingredient phrases against individual parsed ingredient names. */
public final class IngredientFilter {
  private IngredientFilter() {}

  public static boolean matches(String query, List<Ingredient> ingredients) {
    for (String phrase : query.split(",")) {
      String key = Units.key(phrase);
      if (key.isEmpty()) continue;
      boolean found = false;
      // Padding enforces whole whitespace-separated tokens while preserving phrase order.
      String needle = " " + key + " ";
      for (Ingredient ingredient : ingredients) {
        if ((" " + Units.key(ingredient.name) + " ").contains(needle)) {
          found = true;
          break;
        }
      }
      if (!found) return false;
    }
    return true;
  }
}
