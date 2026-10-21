package com.example.recime.data;

import androidx.room.*;

@Entity(tableName = "groceries")
public class Grocery {
  @PrimaryKey(autoGenerate = true)
  public long id;

  public String name = "", unit = "", category = "";
  public Double amount;
  public boolean checked;
}
