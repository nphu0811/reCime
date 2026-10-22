package com.example.recime.model;

import androidx.annotation.NonNull;
import androidx.room.*;

@Entity(
    tableName = "meals",
    indices = {@Index("recipeId")},
    foreignKeys =
        @ForeignKey(
            entity = Recipe.class,
            parentColumns = "id",
            childColumns = "recipeId",
            onDelete = ForeignKey.CASCADE))
public class Meal {
  @PrimaryKey @NonNull public String slot = "";
  public String day = "", type = "";
  public long recipeId;
}
