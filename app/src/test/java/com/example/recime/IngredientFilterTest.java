package com.example.recime;

import static org.junit.Assert.*;

import com.example.recime.helper.IngredientFilter;
import com.example.recime.helper.RecipeParser;
import org.junit.Test;

public class IngredientFilterTest {
  private boolean matches(String query, String ingredients) {
    return IngredientFilter.matches(query, RecipeParser.ingredients(ingredients));
  }

  @Test
  public void matchesWholeMultiwordPhraseAtStartMiddleAndEnd() {
    assertTrue(matches("chicken thigh", "chicken thigh boneless"));
    assertTrue(matches("chicken thigh", "fresh chicken thigh boneless"));
    assertTrue(matches("chicken thigh", "200 g boneless chicken thigh"));
    assertTrue(matches("chicken thigh", "chicken thigh"));
  }

  @Test
  public void normalizesVietnameseCaseAccentsAndWhitespace() {
    assertTrue(matches("  DAU   PHU ", "200 g Đậu phụ non"));
    assertTrue(matches("đậu phụ", "DAU\tPHU non"));
    assertTrue(matches("DUONG", "100 g đường trắng"));
  }

  @Test
  public void commaRequiresEveryPhraseAcrossIngredientRows() {
    String ingredients = "200 g chicken thigh\n1 tsp salt\n100 g rice";
    assertTrue(matches("chicken thigh, salt", ingredients));
    assertTrue(matches(" rice , chicken thigh , salt ", ingredients));
    assertFalse(matches("chicken thigh, pepper", ingredients));
    assertFalse(matches("pepper, salt", ingredients));
  }

  @Test
  public void rejectsSubstringsDifferentCutsAndNoncontiguousOrCrossRowPhrases() {
    assertFalse(matches("chicken thigh", "chicken drumstick"));
    assertFalse(matches("chicken thigh", "chicken thighs"));
    assertFalse(matches("ham", "champignon"));
    assertFalse(matches("rice", "ricewater"));
    assertFalse(matches("chicken thigh", "chicken boneless thigh"));
    assertFalse(matches("chicken thigh", "chicken\nthigh"));
    assertFalse(matches("thigh chicken", "chicken thigh"));
  }

  @Test
  public void treatsEmptyPhrasesAsNoRestriction() {
    assertTrue(matches("", ""));
    assertTrue(matches(" , , ", ""));
    assertTrue(matches(", salt, ,", "1 tsp salt"));
    assertFalse(matches("salt", ""));
  }

  @Test
  public void treatsRegexMetacharactersLiterally() {
    assertFalse(matches(".*", "salt"));
    assertTrue(matches("a+b", "a+b powder"));
  }
}
