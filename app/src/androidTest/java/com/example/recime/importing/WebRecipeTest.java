package com.example.recime.importing;

import static org.junit.Assert.*;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.recime.domain.RecipeParser;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class WebRecipeTest {
  @Test
  public void selfCreatedJsonLdGraphAndHowToSectionsParseToReview() throws Exception {
    String fixture =
        "{\"@graph\":[{\"@type\":\"WebSite\"},{\"@type\":[\"Recipe\"],\"name\":\"Đậu &amp;"
            + " nấm\",\"recipeIngredient\":[\"200 g đậu\",\"1 tbsp"
            + " dầu\"],\"recipeInstructions\":[{\"@type\":\"HowToSection\",\"itemListElement\":[{\"@type\":\"HowToStep\",\"text\":\"<p>Nấu"
            + " chín.</p>\"}]}]}]}";
    JSONObject r = PublicWebExtractor.find(new JSONObject(fixture));
    assertNotNull(r);
    RecipeParser.Parsed p = RecipeParser.parse(PublicWebExtractor.toText(r));
    assertEquals("Đậu & nấm", p.title);
    assertEquals("Nấu chín.", p.steps);
    assertTrue(p.ingredients.contains("200 g"));
    assertNull(PublicWebExtractor.find(new JSONObject("{\"@type\":\"WebSite\"}")));
  }
}
