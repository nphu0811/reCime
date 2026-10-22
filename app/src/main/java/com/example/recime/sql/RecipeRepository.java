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
          // A persistent seed marker prevents deleted recipes from reappearing on restart.
          if (!c.getSharedPreferences("lab", 0).getBoolean("seeded", false)) {
            if (dao.recipeCount() == 0)
              db.runInTransaction(
                  () -> {
                    seed(
                        "Trứng cà chua",
                        "2 quả trứng\n200 g cà chua\n1 muỗng canh dầu ăn\n1 g muối",
                        "Cắt nhỏ cà chua.\n"
                            + "Xào cà chua với dầu, thêm trứng đã đánh.\n"
                            + "Nêm muối và đảo chín.",
                        "Bữa nhanh",
                        "trứng, cà chua, 15 phút");
                    seed(
                        "Đậu phụ sốt nấm",
                        "300 g đậu phụ\n100 g nấm\n2 muỗng canh nước tương\n50 ml nước",
                        "Cắt đậu phụ và nấm.\n"
                            + "Áp chảo đậu, thêm nấm và nước tương.\n"
                            + "Thêm nước và nấu 5 phút.",
                        "Món chay",
                        "chay, nấm, đậu phụ");
                    seed(
                        "Yến mạch chuối",
                        "80 g yến mạch\n200 ml sữa\n2 quả chuối",
                        "Nấu yến mạch với sữa.\nCắt chuối và xếp lên bát.",
                        "Bữa sáng",
                        "chuối, breakfast");
                    seed(
                        "Cơm gà gừng",
                        "300 g thịt gà\n200 g gạo\n20 g gừng\n400 ml nước",
                        "Vo gạo.\nXếp gà và gừng lên gạo.\nThêm nước, nấu chín kỹ thịt gà.",
                        "Món nhà",
                        "gà, cơm");
                  });
            c.getSharedPreferences("lab", 0).edit().putBoolean("seeded", true).apply();
          }
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
          dao.refresh();
          ready.postValue(true);
        });
  }

  private void seed(String title, String ing, String steps, String book, String tags) {
    Recipe r = new Recipe();
    r.title = title;
    r.ingredients = ing;
    r.steps = steps;
    r.cookbook = book;
    r.tags = tags;
    r.source = "Công thức mẫu tự soạn để thử ứng dụng; không phải dữ liệu ReciMe";
    dao.insert(r);
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
