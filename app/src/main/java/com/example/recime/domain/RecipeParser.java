package com.example.recime.domain;

import java.util.*;
import java.util.regex.*;

public final class RecipeParser {
  private static final Pattern AMOUNT =
      Pattern.compile("^(\\d+\\s+\\d+/\\d+|\\d+/\\d+|\\d+(?:[.,]\\d+)?)\\s*(.*)$");
  private static final String[] UNITS = {
    "muỗng cà phê",
    "muỗng canh",
    "tablespoons",
    "tablespoon",
    "teaspoons",
    "teaspoon",
    "kilogram",
    "grams",
    "gram",
    "cups",
    "tbsp",
    "tsp",
    "lạng",
    "quả",
    "củ",
    "cup",
    "kg",
    "ml",
    "g",
    "l"
  };

  public static class Parsed {
    public String title = "", ingredients = "", steps = "";
  }

  public static Ingredient ingredient(String raw) {
    String line =
        raw.trim()
            .replaceFirst("^[•*\\-]\\s*", "")
            .replace("½", " 1/2")
            .replace("¼", " 1/4")
            .replace("¾", " 3/4")
            .trim();
    Matcher m = AMOUNT.matcher(line);
    if (!m.matches()) return new Ingredient(line, null, "");
    double number;
    try {
      number = quantity(m.group(1));
    } catch (IllegalArgumentException e) {
      return new Ingredient(line, null, "");
    }
    String rest = m.group(2).trim();
    for (String u : UNITS) {
      String k = Units.key(rest), uk = Units.key(u);
      if (k.startsWith(uk)
          && (k.length() == uk.length() || Character.isWhitespace(k.charAt(uk.length())))) {
        String name = rest.substring(u.length()).trim();
        if (!name.isEmpty()) return new Ingredient(name, number, Units.canonical(u));
      }
    }
    return rest.isEmpty() ? new Ingredient(line, null, "") : new Ingredient(rest, number, "");
  }

  static double quantity(String s) {
    double total = 0;
    for (String part : s.replace(',', '.').split("\\s+")) {
      if (part.contains("/")) {
        String[] f = part.split("/");
        double den = Double.parseDouble(f[1]);
        if (den == 0) throw new IllegalArgumentException();
        total += Double.parseDouble(f[0]) / den;
      } else total += Double.parseDouble(part);
    }
    if (!Double.isFinite(total) || total < 0) throw new IllegalArgumentException();
    return total;
  }

  public static List<Ingredient> ingredients(String raw) {
    List<Ingredient> result = new ArrayList<>();
    for (String line : raw.split("\\r?\\n"))
      if (!line.trim().isEmpty()) result.add(ingredient(line));
    return result;
  }

  public static Parsed parse(String raw) {
    Parsed p = new Parsed();
    int section = 0;
    StringBuilder ing = new StringBuilder(), steps = new StringBuilder();
    for (String line : raw.split("\\r?\\n")) {
      line = line.trim();
      if (line.isEmpty()) continue;
      String k = Units.key(line).replaceAll(":$", "");
      if (k.matches("nguyen lieu|ingredients")) {
        section = 1;
        continue;
      }
      if (k.matches("cach lam|cac buoc|huong dan|instructions|method|directions|steps")) {
        section = 2;
        continue;
      }
      if (p.title.isEmpty() && section == 0) {
        p.title = line;
        continue;
      }
      if (section == 2 || (section == 0 && line.matches("^\\d+[.)].*"))) {
        section = 2;
        steps.append(line.replaceFirst("^\\d+[.)]\\s*", "")).append('\n');
      } else ing.append(line).append('\n');
    }
    p.ingredients = ing.toString().trim();
    p.steps = steps.toString().trim();
    return p;
  }
}
