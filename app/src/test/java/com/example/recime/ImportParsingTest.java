package com.example.recime;
import com.example.recime.helper.*;
import com.example.recime.model.ParsedRecipe;
import org.junit.Test;
import static org.junit.Assert.*;
public class ImportParsingTest {
  @Test public void vietnameseServingsAreMetadataAndDoNotInventQuantities() {
    ParsedRecipe p = RecipeParser.parse("Canh rau\nKhẩu phần: 3 người\nNguyên liệu:\nrau tùy thích\nCách làm:\nRửa rau\nNấu rau");
    assertEquals(3, p.servings, 0);
    assertEquals("rau tùy thích", p.ingredients);
    assertNull(RecipeParser.ingredient(p.ingredients).amount);
    assertEquals("Rửa rau\nNấu rau", p.steps);
  }
  @Test public void missingServingsAndProvenanceStayExplicit() {
    ParsedRecipe p = RecipeParser.parse("Rice\nAttribution: Test Author CC BY-SA\nIngredients\n100 g rice\nInstructions\nCook");
    assertNull(p.servings);
    assertEquals("Test Author CC BY-SA", p.attribution);
    assertEquals("100 g rice", p.ingredients);
    assertNull(RecipeParser.parse("Rice\nServings: 2-4\nIngredients\nrice").servings);
  }
  @Test public void providerCapabilitiesDoNotConfuseLookalikesOrVideoWithRecipe() {
    assertTrue(ImportProviders.forUrl("https://www.tiktok.com/@a/video/1").metadataOnly);
    assertEquals(ImportProviders.Status.PROVIDER_UNSUPPORTED, ImportProviders.forUrl("https://youtu.be/123").status);
    assertFalse(ImportProviders.forUrl("https://mytiktok.com/recipe").metadataOnly);
    assertEquals(ImportProviders.Status.PROVIDER_UNSUPPORTED, ImportProviders.forUrl("https://user@tiktok.com/video/1").status);
  }
  @Test public void shareCaptionAndUrlAreBothPreserved() {
    SharedRecipeInput input = SharedRecipeInput.parse("Try this https://example.org/recipe\n100 g rice\nCook");
    assertEquals("https://example.org/recipe", input.url);
    assertTrue(input.text.contains("100 g rice"));
    assertEquals("", SharedRecipeInput.parse("https://example.org/recipe").text);
    assertEquals("", SharedRecipeInput.parse(null).url);
  }
}
