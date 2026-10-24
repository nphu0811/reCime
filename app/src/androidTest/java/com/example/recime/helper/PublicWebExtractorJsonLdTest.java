package com.example.recime.helper;

import static org.junit.Assert.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Uses synthetic public-schema fixtures; no network or third-party content. */
@RunWith(AndroidJUnit4.class)
public class PublicWebExtractorJsonLdTest {
  @Test public void findsFullSchemaTypeInsideNestedWebPage() throws Exception {
    JSONObject recipe = new JSONObject()
        .put("@type", new JSONArray().put("CreativeWork").put("https://schema.org/Recipe"))
        .put("name", "Rice &amp; tofu")
        .put("recipeIngredient", new JSONArray().put("200 g rice").put("100 g tofu"))
        .put("recipeInstructions", new JSONArray()
            .put(new JSONObject().put("@type", "HowToSection")
                .put("itemListElement", new JSONArray()
                    .put(new JSONObject().put("@type", "HowToStep").put("text", "Wash rice"))
                    .put(new JSONObject().put("@type", "HowToStep").put("text", "Cook tofu")))));
    JSONObject page = new JSONObject().put("@type", "WebPage").put("mainEntity", recipe);
    JSONObject script = new JSONObject().put("@graph",
        new JSONArray().put(new JSONObject().put("@type", "Organization")).put(page));

    JSONObject found = PublicWebExtractor.find(script);
    assertNotNull(found);
    assertEquals("Rice &amp; tofu", found.getString("name"));
    String text = PublicWebExtractor.toText(found);
    assertTrue(text, text.contains("Rice & tofu"));
    assertTrue(text, text.contains("200 g rice"));
    assertTrue(text, text.contains("100 g tofu"));
    assertTrue(text, text.contains("Wash rice"));
    assertTrue(text, text.contains("Cook tofu"));
  }

  @Test public void supportsSingleIngredientAndStepStrings() throws Exception {
    JSONObject recipe = new JSONObject()
        .put("@type", "http://schema.org/Recipe")
        .put("name", "Simple rice")
        .put("recipeIngredient", "200 g rice")
        .put("recipeInstructions", "Cook in water.");
    JSONObject found = PublicWebExtractor.find(recipe);
    assertNotNull(found);
    String text = PublicWebExtractor.toText(found);
    assertTrue(text, text.contains("200 g rice"));
    assertTrue(text, text.contains("Cook in water."));
  }

  @Test public void neverTreatsSimilarButDifferentTypesAsRecipe() throws Exception {
    JSONObject nonRecipe = new JSONObject()
        .put("@type", new JSONArray().put("HowTo").put("NotARecipe"))
        .put("name", "No recipe here");
    assertNull(PublicWebExtractor.find(nonRecipe));
    JSONObject nested = new JSONObject().put("itemListElement",
        new JSONArray().put(nonRecipe).put(new JSONObject().put("@type", "Recipe")));
    assertNotNull(PublicWebExtractor.find(nested));
  }
}
