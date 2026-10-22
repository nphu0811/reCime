package com.example.recime.sql;

import android.content.Context;
import androidx.lifecycle.*;
import com.example.recime.helper.CatalogImporter;
import com.example.recime.helper.GroceryAggregator;
import com.example.recime.helper.Units;
import com.example.recime.model.Grocery;
import com.example.recime.model.Ingredient;
import com.example.recime.model.Recipe;
import java.util.*;
import java.util.concurrent.*;

public class RecipeRepository {
  private static volatile RecipeRepository instance;
  public final LabDatabase db;
  public final LabDao dao;
  public final ExecutorService io = Executors.newSingleThreadExecutor();
  public final ExecutorService extraction = Executors.newFixedThreadPool(2);
  public final MutableLiveData<String> message = new MutableLiveData<>();
  public final MutableLiveData<Boolean> ready = new MutableLiveData<>(false);

  public static RecipeRepository get(Context c) {
    if (instance == null)
      synchronized (RecipeRepository.class) {
        if (instance == null) instance = new RecipeRepository(c.getApplicationContext());
      }
    return instance;
  }

  private RecipeRepository(Context c) {
    db = new LabDatabase(c);
    dao = db.dao();
    run(
        () -> {
          // Preserve existing recipes; new installations only receive the attributed catalog.
          if (!c.getSharedPreferences("lab", 0).getBoolean("catalog-v2-547-photos178", false)) {
            int count;
            try {
              count = CatalogImporter.install(c, db);
            } catch (Exception e) {
              throw new IllegalStateException("Không nạp được thư viện mở", e);
            }
            c.getSharedPreferences("lab", 0)
                .edit()
                .putBoolean("catalog-v2-547-photos178", true)
                .apply();
            message.postValue("Đã cập nhật thư viện offline · " + count + " công thức mới.");
          }
          // Recompute the derived cart after upgrading a v1/v2/v3 database or restarting.
          // Checked states survive because they are keyed by normalized ingredient + unit.
          dao.syncPlanGroceries();
          dao.refresh();
          ready.postValue(true);
        });
  }

  public void run(Runnable action) {
    io.execute(
        () -> {
          try {
            action.run();
          } catch (Exception e) {
            message.postValue("Lỗi: " + e.getMessage());
          }
        });
  }

  public void save(Recipe r) {
    run(
        () -> {
          if (r.id == 0) r.id = dao.insert(r);
          else dao.update(r);
          message.postValue("Đã lưu công thức");
        });
  }

  public void delete(Recipe r) {
    run(() -> db.deleteRecipe(r));
  }

  public void addIngredients(List<Ingredient> values, double tsp, double tbsp, double cup) {
    run(
        () ->
            db.runInTransaction(
                () -> {
                  for (Ingredient i : GroceryAggregator.merge(values, tsp, tbsp, cup)) {
                    Grocery match = null;
                    if (i.amount != null)
                      for (Grocery g : dao.grocerySnapshot())
                        if (!g.checked
                            && g.amount != null
                            && Units.key(g.name).equals(Units.key(i.name))
                            && Units.dimension(g.unit).equals(Units.dimension(i.unit))) {
                          match = g;
                          break;
                        }
                    if (match != null) {
                      match.amount += Units.convert(i.amount, i.unit, match.unit, tsp, tbsp, cup);
                      dao.updateGrocery(match);
                    } else {
                      Grocery g = new Grocery();
                      g.name = i.name;
                      g.amount = i.amount;
                      g.unit = i.unit;
                      g.category = GroceryAggregator.category(i.name);
                      dao.insertGrocery(g);
                    }
                  }
                  message.postValue("Đã thêm nguyên liệu vào giỏ");
                }));
  }
}
