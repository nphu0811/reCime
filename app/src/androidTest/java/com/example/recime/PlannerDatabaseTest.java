package com.example.recime;
import static org.junit.Assert.*;
import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.recime.sql.*;
import com.example.recime.model.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class PlannerDatabaseTest {
  private LabDatabase db;private Recipe recipe;
  private final Context context=ApplicationProvider.getApplicationContext();
  @Before public void setup(){
    db=new LabDatabase(context,null);recipe=new Recipe();recipe.title="Planner preservation";
    recipe.ingredients="200 g gạo";recipe.steps="Nấu";recipe.servings=2;recipe.favorite=true;
    recipe.cookbook="Giữ cookbook";recipe.setPhotoPaths(Arrays.asList("photo-a","photo-b"));
    recipe.id=db.dao().insert(recipe);
  }
  @After public void close(){db.close();}
  private Meal meal(String day,String type,double servings){
    Meal m=new Meal();m.day=day;m.type=type;m.slot=day+"|"+type;m.recipeId=recipe.id;m.servings=servings;return m;
  }
  private Meal extra(String day,String type,double servings){
    Meal m=meal(day,type,servings);m.extraId=db.dao().addMeal(m);return m;
  }
  private Grocery planned(){return db.dao().plannedGrocerySnapshot().get(0);}
  @Test public void moveAndRemovePreserveOtherDishesCheckedStateAndUserData(){
    Meal source=extra("2026-10-23","Sáng",4),destination=extra("2026-10-24","Tối",2);
    Grocery manual=new Grocery();manual.name="Gạo thủ công";manual.amount=500.0;manual.unit="g";manual.checked=true;
    manual.id=db.dao().insertGrocery(manual);
    Grocery checked=planned();checked.checked=true;db.dao().updateGrocery(checked);
    db.dao().moveMeal(source,destination.day,destination.type);
    assertEquals(2,db.dao().mealSnapshot(destination.day,destination.day).size());
    assertTrue(db.dao().mealSnapshot(source.day,source.day).isEmpty());
    assertEquals(600.0,planned().amount,0.001);assertTrue(planned().checked);
    Meal moved=db.dao().mealSnapshot(destination.day,destination.day).stream().filter(m->m.extraId==source.extraId).findFirst().get();
    assertEquals(4.0,moved.servings,0.001);db.dao().deleteMeal(moved);
    assertEquals(200.0,planned().amount,0.001);assertTrue(planned().checked);
    db.dao().deleteMeal(destination);assertTrue(db.dao().plannedGrocerySnapshot().isEmpty());
    assertEquals(500.0,db.dao().grocerySnapshot().get(0).amount,0.001);assertTrue(db.dao().grocerySnapshot().get(0).checked);
    Recipe saved=db.dao().find(recipe.id);assertTrue(saved.favorite);assertEquals(recipe.photoPaths(),saved.photoPaths());
    assertEquals(Collections.singletonList(recipe.cookbook),db.dao().cookbookNamesForRecipe(recipe.id));
  }
  @Test public void legacyMoveIntoOccupiedLegacySlotNeverReplacesDestination(){
    Meal legacy=meal("2026-10-23","Sáng",0),occupied=meal("2026-10-24","Trưa",0);
    db.dao().putMeal(legacy);db.dao().putMeal(occupied);
    db.dao().moveMeal(legacy,occupied.day,occupied.type);
    List<Meal> saved=db.dao().mealSnapshot(occupied.day,occupied.day);assertEquals(2,saved.size());
    Meal converted=saved.stream().filter(m->m.extraId>0).findFirst().get();
    assertEquals(0.0,converted.servings,0.001);assertEquals(400.0,planned().amount,0.001);
    db.dao().editMeal(converted,converted.day,converted.type,recipe.id,3);
    assertEquals(500.0,planned().amount,0.001);
    assertThrows(IllegalStateException.class,()->db.dao().moveMeal(legacy,occupied.day,occupied.type));
    assertEquals(2,db.dao().mealSnapshot(null,null).size());
  }
  @Test public void moveFailureAndSyncFailureRollbackScheduleAndGroceries(){
    Meal source=extra("2026-10-23","Sáng",3);
    assertThrows(RuntimeException.class,()->db.dao().editMeal(source,"2026-10-24","Tối",999999,3));
    assertEquals(source.day,db.dao().mealSnapshot(null,null).get(0).day);assertEquals(300.0,planned().amount,0.001);
    Grocery checked=planned();checked.checked=true;db.dao().updateGrocery(checked);
    db.getWritableDatabase().execSQL("CREATE TEMP TRIGGER qa_sync_failure BEFORE DELETE ON plan_groceries BEGIN SELECT RAISE(ABORT,'QA sync rejected'); END");
    try {
      assertThrows(RuntimeException.class,()->db.dao().moveMeal(source,"2026-10-24","Tối"));
      assertThrows(RuntimeException.class,()->db.dao().clearMealWeek("2026-10-19","2026-10-25"));
      assertEquals(source.day,db.dao().mealSnapshot(null,null).get(0).day);
      assertEquals(300.0,planned().amount,0.001);assertTrue(planned().checked);
    } finally {db.getWritableDatabase().execSQL("DROP TRIGGER qa_sync_failure");}
  }
  @Test public void movingAnOlderSnapshotKeepsNewerServingOverride(){
    Meal before=extra("2026-10-23","Sáng",4);
    db.dao().editMeal(before,before.day,before.type,recipe.id,3);
    db.dao().moveMeal(before,"2026-10-24","Trưa");
    Meal saved=db.dao().mealSnapshot(null,null).get(0);
    assertEquals(before.extraId,saved.extraId);assertEquals(3,saved.servings,0.001);
    assertEquals(300.0,planned().amount,0.001);
  }
  @Test public void weekClearKeepsOtherWeekAndManualIngredients(){
    extra("2026-12-31","Tối",2);extra("2027-01-04","Sáng",4);
    Grocery manual=new Grocery();manual.name="Không xóa";db.dao().insertGrocery(manual);
    db.dao().clearMealWeek("2026-12-28","2027-01-03");
    assertEquals("2027-01-04",db.dao().mealSnapshot(null,null).get(0).day);
    assertEquals(400.0,planned().amount,0.001);assertEquals(1,db.dao().grocerySnapshot().size());
    assertNotNull(db.dao().find(recipe.id));
  }
}
