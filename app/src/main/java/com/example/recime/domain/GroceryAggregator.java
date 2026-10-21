package com.example.recime.domain;

import java.util.*;

public final class GroceryAggregator {
  public static List<Ingredient> merge(
      List<Ingredient> input, double tsp, double tbsp, double cup) {
    Map<String, Ingredient> map = new LinkedHashMap<>();
    int unknown = 0;
    for (Ingredient i : input) {
      if (i.amount == null) {
        map.put("unknown:" + (unknown++), i);
        continue;
      }
      String dim = Units.dimension(i.unit),
          unit = dim.equals("mass") ? "g" : dim.equals("volume") ? "ml" : i.unit;
      double value = Units.convert(i.amount, i.unit, unit, tsp, tbsp, cup);
      String key = Units.key(i.name) + "|" + dim;
      Ingredient old = map.get(key);
      map.put(key, new Ingredient(i.name, value + (old == null ? 0 : old.amount), unit));
    }
    return new ArrayList<>(map.values());
  }

  public static String category(String name) {
    String k = Units.key(name);
    if (k.matches(".*(rau|ca chua|hanh|carrot|chuoi|banana|broccoli|carot|khoai).*"))
      return "Rau & trái cây";
    if (k.matches(".*(trung|thit|ga|beef|egg|tofu|dau phu).*")) return "Đạm & trứng";
    if (k.matches(".*(sua|milk|cheese|pho mai|butter).*")) return "Sữa & bơ";
    return "Đồ khô & gia vị";
  }
}
