package com.example.recime.model;

import com.example.recime.helper.Units;

public class Ingredient {
  public final String name, unit;
  public final Double amount;

  public Ingredient(String name, Double amount, String unit) {
    this.name = name.trim();
    this.amount = amount;
    this.unit = unit;
  }

  public Ingredient scale(double factor) {
    if (!Double.isFinite(factor) || factor <= 0)
      throw new IllegalArgumentException("Khẩu phần phải lớn hơn 0");
    return new Ingredient(name, amount == null ? null : amount * factor, unit);
  }

  public String display() {
    return (amount == null ? "" : Units.format(amount) + " " + unit + " ") + name;
  }
}
