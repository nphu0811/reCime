package com.example.recime;

import static org.junit.Assert.*;
import com.example.recime.helper.ImportDuplicateChecker;
import com.example.recime.model.ParsedRecipe;
import com.example.recime.model.Recipe;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class ImportDuplicateCheckerTest {
  private static ParsedRecipe draft(String title, String ingredients, String steps) {
    ParsedRecipe result = new ParsedRecipe();
    result.title = title;
    result.ingredients = ingredients;
    result.steps = steps;
    return result;
  }

  private static Recipe saved(String title, String ingredients, String steps, String source) {
    Recipe recipe = new Recipe();
    recipe.id = 42;
    recipe.title = title;
    recipe.ingredients = ingredients;
    recipe.steps = steps;
    recipe.source = source;
    return recipe;
  }

  @Test public void sameHttpsSourceIsSuggestedEvenIfTitleChanged() {
    Recipe original = saved("Old title", "rice", "cook", "https://Recipes.Example/food/42/");
    assertSame(original, ImportDuplicateChecker.find(Collections.singletonList(original),
        draft("New title", "tofu", "steam"), "https://recipes.example/food/42"));
  }

  @Test public void differentQueryIdsAndHttpDoNotLookLikeSameSource() {
    Recipe original = saved("A", "rice", "cook", "https://recipes.example/view?id=1");
    assertNull(ImportDuplicateChecker.find(Collections.singletonList(original),
        draft("B", "tofu", "steam"), "https://recipes.example/view?id=2"));
    assertNull(ImportDuplicateChecker.find(Collections.singletonList(original),
        draft("B", "tofu", "steam"), "http://recipes.example/view?id=1"));
  }

  @Test public void matchesIdenticalContentAfterVietnameseNormalization() {
    Recipe original = saved("Đậu phụ", "200 g ĐẬU PHỤ", "  Hấp cách thủy ", "");
    assertSame(original, ImportDuplicateChecker.find(Collections.singletonList(original),
        draft("dau phu", "200 G dau phu", "hap cach thuy"), ""));
  }

  @Test public void partialOrDifferentContentNeverMatches() {
    Recipe original = saved("Rice", "200 g rice", "Cook for ten minutes", "");
    assertNull(ImportDuplicateChecker.find(Collections.singletonList(original),
        draft("Rice", "200 g rice", "Cook for five minutes"), ""));
    assertNull(ImportDuplicateChecker.find(Collections.singletonList(original),
        draft("Rice", "", ""), ""));
  }

  @Test public void catalogAndNullInputsAreIgnored() {
    Recipe catalog = saved("Rice", "rice", "cook", "https://recipes.example/42");
    catalog.catalogKey = "offline-42";
    ParsedRecipe proposed = draft("Rice", "rice", "cook");
    assertNull(ImportDuplicateChecker.find(Collections.singletonList(catalog),
        proposed, "https://recipes.example/42"));
    assertNull(ImportDuplicateChecker.find(null, proposed, ""));
    assertNull(ImportDuplicateChecker.find(Arrays.asList(catalog), null, ""));
  }

  @Test public void freeFormSourceAndLookalikeHostnameDoNotTriggerSourceMatch() {
    Recipe original = saved("Rice", "rice", "cook", "Source: family");
    assertNull(ImportDuplicateChecker.find(Collections.singletonList(original),
        draft("Tofu", "tofu", "steam"), "Source: family"));
    Recipe url = saved("Rice", "rice", "cook", "https://recipes.example/42");
    assertNull(ImportDuplicateChecker.find(Collections.singletonList(url),
        draft("Tofu", "tofu", "steam"), "https://recipes.example.attacker/42"));
  }
}
