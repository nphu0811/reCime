package com.example.recime.helper;

import java.text.Normalizer;
import java.util.Locale;

public final class Units {
  private Units() {}

  public static String key(String s) {
    return Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .replace('đ', 'd')
        .trim()
        .replaceAll("\\s+", " ");
  }

  public static String canonical(String s) {
    switch (key(s)) {
      case "gram":
      case "grams":
        return "g";
      case "kilogram":
        return "kg";
      case "lang":
        return "lạng";
      case "lit":
      case "liter":
      case "litre":
        return "l";
      case "muong canh":
      case "tablespoon":
      case "tablespoons":
        return "tbsp";
      case "muong ca phe":
      case "teaspoon":
      case "teaspoons":
        return "tsp";
      case "cups":
        return "cup";
      case "qua":
        return "quả";
      case "cu":
        return "củ";
      default:
        return s.toLowerCase(Locale.ROOT).trim();
    }
  }

  public static String dimension(String unit) {
    switch (canonical(unit)) {
      case "g":
      case "kg":
      case "lạng":
        return "mass";
      case "ml":
      case "l":
      case "tsp":
      case "tbsp":
      case "cup":
        return "volume";
      default:
        return "count:" + canonical(unit);
    }
  }

  public static double factor(String unit, double tsp, double tbsp, double cup) {
    switch (canonical(unit)) {
      case "kg":
      case "l":
        return 1000;
      case "lạng":
        return 100;
      case "tsp":
        return tsp;
      case "tbsp":
        return tbsp;
      case "cup":
        return cup;
      default:
        return 1;
    }
  }

  public static double convert(
      double value, String from, String to, double tsp, double tbsp, double cup) {
    if (!Double.isFinite(value)
        || value < 0
        || tsp <= 0
        || tbsp <= 0
        || cup <= 0
        || !dimension(from).equals(dimension(to)))
      throw new IllegalArgumentException("Không thể đổi đơn vị khác thứ nguyên");
    return value * factor(from, tsp, tbsp, cup) / factor(to, tsp, tbsp, cup);
  }

  public static String format(double d) {
    return String.format(Locale.ROOT, "%.3f", d).replaceAll("0+$", "").replaceAll("\\.$", "");
  }
}
