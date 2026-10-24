package com.example.recime.model;

public class Grocery {
  public Grocery() {}
  public Grocery(Grocery other){
    id=other.id;name=other.name;unit=other.unit;category=other.category;
    amount=other.amount;checked=other.checked;planned=other.planned;
  }
  public long id;

  public String name = "", unit = "", category = "";
  public Double amount;
  public boolean checked;
  /** Derived automatically from meal plan; edited only by changing the plan. */
  public boolean planned;
}
