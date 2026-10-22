package com.example.recime.data;

import androidx.room.*;

@Database(
    entities = {Recipe.class, Meal.class, Grocery.class, CatalogTombstone.class},
    version = 3,
    exportSchema = true)
public abstract class LabDatabase extends RoomDatabase {
  public static final androidx.room.migration.Migration MIGRATION_1_2 =
      new androidx.room.migration.Migration(1, 2) {
        @Override
        public void migrate(androidx.sqlite.db.SupportSQLiteDatabase db) {
          db.execSQL("ALTER TABLE recipes ADD COLUMN catalogKey TEXT");
          db.execSQL("ALTER TABLE recipes ADD COLUMN attribution TEXT DEFAULT ''");
          db.execSQL("ALTER TABLE recipes ADD COLUMN imageAttribution TEXT DEFAULT ''");
          db.execSQL("ALTER TABLE recipes ADD COLUMN servingsKnown INTEGER NOT NULL DEFAULT 1");
          db.execSQL(
              "CREATE UNIQUE INDEX IF NOT EXISTS index_recipes_catalogKey ON recipes(catalogKey)");
        }
      };
  public static final androidx.room.migration.Migration MIGRATION_2_3 =
      new androidx.room.migration.Migration(2, 3) {
        @Override
        public void migrate(androidx.sqlite.db.SupportSQLiteDatabase db) {
          db.execSQL(
              "CREATE TABLE IF NOT EXISTS catalog_tombstones (catalogKey TEXT NOT NULL PRIMARY KEY,"
                  + " deletedAt INTEGER NOT NULL)");
        }
      };

  public void deleteRecipe(Recipe recipe) {
    runInTransaction(
        () -> {
          if (recipe.catalogKey != null) {
            CatalogTombstone tombstone = new CatalogTombstone();
            tombstone.catalogKey = recipe.catalogKey;
            tombstone.deletedAt = System.currentTimeMillis();
            dao().putTombstone(tombstone);
          }
          dao().delete(recipe);
        });
  }

  public abstract LabDao dao();
}
