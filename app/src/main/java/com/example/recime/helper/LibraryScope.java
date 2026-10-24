package com.example.recime.helper;
import com.example.recime.model.Recipe;
/** Catalog rows remain attributed and separate from personal recipes. */
public final class LibraryScope {
  private LibraryScope() {}
  public static boolean includes(Recipe recipe, boolean discover) {
    return (recipe.catalogKey != null) == discover;
  }
}
