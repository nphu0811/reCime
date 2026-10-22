package com.example.recime.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "catalog_tombstones")
public class CatalogTombstone {
  @PrimaryKey @NonNull public String catalogKey = "";
  public long deletedAt;
}
