package com.example.recime;

import static org.junit.Assert.*;
import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.recime.model.Grocery;
import com.example.recime.model.Meal;
import com.example.recime.model.Recipe;
import com.example.recime.sql.LabDatabase;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Regression: real cookbook CRUD, two dishes per slot, derived + manual groceries. */
@RunWith(AndroidJUnit4.class)
public class LabV4FeaturesTest {
  @Test public void v4CollectionsAndPlannerKeepUserData(){
    Context context=ApplicationProvider.getApplicationContext();
    String name="test-v4-"+System.nanoTime()+".db";
    LabDatabase db=new LabDatabase(context,name);
    try{
      assertEquals(4,db.getWritableDatabase().getVersion());
      Recipe r=new Recipe();r.title="Cơm";r.ingredients="200 g gạo";
      r.steps="Nấu";r.cookbook="Món nhà";
      r.id=db.dao().insert(r);
      db.dao().createCookbook("Thứ bảy");
      assertTrue(db.dao().bookNamesSnapshot().contains("Thứ bảy"));
      db.dao().addRecipeToCookbook(r.id,"Thứ bảy");
      assertTrue(db.dao().recipeIdsForCookbook("Thứ bảy").contains(r.id));
      db.dao().renameCookbook("Thứ bảy","Ăn cuối tuần");
      assertTrue(db.dao().recipeIdsForCookbook("Ăn cuối tuần").contains(r.id));
      assertTrue(db.dao().bookNamesSnapshot().contains("Ăn cuối tuần"));
      db.dao().deleteCookbook("Ăn cuối tuần");
      assertNotNull(db.dao().find(r.id));

      Recipe second=new Recipe();second.title="Cháo";
      second.ingredients="100 g gạo";second.steps="Nấu";
      second.id=db.dao().insert(second);
      Meal one=new Meal();one.day="2026-10-22";one.type="Trưa";
      one.slot=one.day+"|"+one.type;one.recipeId=r.id;
      db.dao().putMeal(one);
      Meal two=new Meal();two.day=one.day;two.type=one.type;
      two.slot=one.slot;two.recipeId=second.id;
      db.dao().addMeal(two);
      assertEquals(2,db.dao().mealSnapshot(one.day,one.day).size());

      List<Grocery> derived=db.dao().plannedGrocerySnapshot();
      assertEquals(1,derived.size());
      assertTrue(derived.get(0).planned);
      assertEquals(300.0,derived.get(0).amount,0.001);
      Grocery checked=derived.get(0);
      checked.checked=true;
      db.dao().updateGrocery(checked);
      db.dao().syncPlanGroceries();
      assertEquals(1,db.dao().plannedGrocerySnapshot().size());
      assertTrue(db.dao().plannedGrocerySnapshot().get(0).checked);

      Grocery manual=new Grocery();manual.name="Sữa";manual.unit="ml";
      manual.amount=200.0;manual.category="Sữa";
      db.dao().insertGrocery(manual);
      db.close();
      db=new LabDatabase(context,name);
      assertEquals(2,db.dao().mealSnapshot(one.day,one.day).size());
      assertEquals(1,db.dao().grocerySnapshot().size());
      db.dao().deleteMeal(db.dao().mealSnapshot(one.day,one.day).get(1));
      assertEquals(1,db.dao().mealSnapshot(one.day,one.day).size());
      assertEquals(200.0,db.dao().plannedGrocerySnapshot().get(0).amount,0.001);
      db.dao().clearMealWeek("2026-10-20","2026-10-26");
      assertTrue(db.dao().mealSnapshot(one.day,one.day).isEmpty());
      assertTrue(db.dao().plannedGrocerySnapshot().isEmpty());
      assertEquals(1,db.dao().grocerySnapshot().size());
    }finally{db.close();context.deleteDatabase(name);}
  }
}
