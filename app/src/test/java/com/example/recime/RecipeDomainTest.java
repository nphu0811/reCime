package com.example.recime;

import static org.junit.Assert.*;

import com.example.recime.domain.*;
import java.util.*;
import org.junit.Test;

public class RecipeDomainTest {
  @Test
  public void portionsAlwaysScaleOriginalAndDoNotMutate() {
    Ingredient original = new Ingredient("bột", 150.0, "g");
    assertEquals(225, original.scale(3.0 / 2).amount, 0.00001);
    assertEquals(75, original.scale(1.0 / 2).amount, 0.00001);
    assertEquals(150, original.amount, 0.00001);
    assertNull(new Ingredient("muối", null, "").scale(2).amount);
  }

  @Test
  public void metricAndConfiguredVolumeConversions() {
    assertEquals(1500, Units.convert(1.5, "kg", "g", 5, 15, 240), 0.00001);
    assertEquals(200, Units.convert(2, "lạng", "g", 5, 15, 240), 0.00001);
    assertEquals(0.5, Units.convert(500, "ml", "l", 5, 15, 240), 0.00001);
    assertEquals(3, Units.convert(1, "muỗng canh", "tsp", 5, 15, 240), 0.00001);
    assertEquals(250, Units.convert(1, "cup", "ml", 5, 15, 250), 0.00001);
  }

  @Test(expected = IllegalArgumentException.class)
  public void refusesMassVolumeWithoutDensity() {
    Units.convert(100, "g", "ml", 5, 15, 240);
  }

  @Test(expected = IllegalArgumentException.class)
  public void refusesDifferentCountUnits() {
    Units.convert(2, "quả", "củ", 5, 15, 240);
  }

  @Test(expected = IllegalArgumentException.class)
  public void refusesInvalidPortions() {
    new Ingredient("gạo", 100.0, "g").scale(0);
  }

  @Test
  public void vietnameseAndEnglishUnitsAndFractions() {
    String[] rows = {
      "2 muỗng canh dầu ăn",
      "1 muỗng cà phê muối",
      "250 g bột",
      "0,5 kg gạo",
      "100 ml nước",
      "1 l sữa",
      "2 quả trứng",
      "3 củ khoai",
      "2 lạng thịt",
      "1 1/2 cups flour",
      "½ tsp salt",
      "200g đậu"
    };
    String[] units = {"tbsp", "tsp", "g", "kg", "ml", "l", "quả", "củ", "lạng", "cup", "tsp", "g"};
    for (int i = 0; i < rows.length; i++) {
      Ingredient r = RecipeParser.ingredient(rows[i]);
      assertEquals(rows[i], units[i], r.unit);
      assertNotNull(rows[i], r.amount);
      assertFalse(r.name.isEmpty());
    }
    assertEquals(1.5, RecipeParser.ingredient(rows[9]).amount, 0.00001);
    assertEquals(0.5, RecipeParser.ingredient(rows[10]).amount, 0.00001);
  }

  @Test
  public void parserPreservesUnknownQuantitiesAndSeparatesSteps() {
    RecipeParser.Parsed p =
        RecipeParser.parse(
            "Đậu sốt\n"
                + "Nguyên liệu:\n"
                + "300 g đậu phụ\n"
                + "muối vừa đủ\n"
                + "Cách làm:\n"
                + "1. Cắt đậu.\n"
                + "2. Nấu chín.");
    assertEquals("Đậu sốt", p.title);
    assertTrue(p.ingredients.contains("muối vừa đủ"));
    assertFalse(p.ingredients.contains("Cắt"));
    assertEquals("Cắt đậu.\nNấu chín.", p.steps);
    assertNull(RecipeParser.ingredient("muối vừa đủ").amount);
    assertNull(RecipeParser.ingredient("1/0 g lỗi").amount);
    RecipeParser.Parsed english =
        RecipeParser.parse("Bread\nIngredients\n1 cup flour\nDirections\nMix.\nBake.");
    assertEquals("Mix.\nBake.", english.steps);
  }

  @Test
  public void groceryAggregationNormalizesCompatibleDimensionsOnly() {
    List<Ingredient> merged =
        GroceryAggregator.merge(
            Arrays.asList(
                new Ingredient("Gạo", 0.5, "kg"), new Ingredient("gạo", 250.0, "g"),
                new Ingredient("dầu", 1.0, "tbsp"), new Ingredient("Dầu", 5.0, "ml"),
                new Ingredient("gạo", 1.0, "cup"), new Ingredient("trứng", 2.0, "quả"),
                new Ingredient("trứng", 1.0, "củ"), new Ingredient("muối", null, "")),
            5,
            15,
            240);
    assertEquals(6, merged.size());
    assertEquals(750, merged.get(0).amount, 0.00001);
    assertEquals("g", merged.get(0).unit);
    assertEquals(20, merged.get(1).amount, 0.00001);
    assertEquals("ml", merged.get(2).unit);
    assertNull(merged.get(5).amount);
  }

  @Test
  public void searchNormalizesVietnameseAccents() {
    assertEquals("dau phu", Units.key("  Đậu PHỤ "));
  }
}
