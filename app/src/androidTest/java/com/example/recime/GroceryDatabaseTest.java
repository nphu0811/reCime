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
public class GroceryDatabaseTest {
  private final Context context=ApplicationProvider.getApplicationContext();
  private LabDatabase db;private String name;private Recipe recipe;private Meal meal;
  @Before public void setup(){
    name="grocery-qa-"+System.nanoTime()+".db";db=new LabDatabase(context,name);
    recipe=new Recipe();recipe.title="Preserve recipe";recipe.cookbook="Keep book";recipe.servings=2;
    recipe.ingredients="200 g rice";recipe.steps="Cook";recipe.setPhotoPaths(Arrays.asList("cover","second"));recipe.favorite=true;
    recipe.id=db.dao().insert(recipe);meal=new Meal();meal.day="2030-01-01";meal.type="Sáng";meal.recipeId=recipe.id;meal.servings=4;meal.extraId=db.dao().addMeal(meal);
  }
  @After public void cleanup(){db.close();context.deleteDatabase(name);}
  private Grocery manual(String name,Double amount,String unit,boolean checked){
    Grocery g=new Grocery();g.name=name;g.amount=amount;g.unit=unit;g.category="Custom";g.checked=checked;g.id=db.dao().insertGrocery(g);return g;
  }
  private void batch(Ingredient... values){db.dao().addGroceryBatch(Arrays.asList(values),5,15,240,null);}
  @Test public void batchNormalizesUnitsWithoutMergingCheckedOrPlannedRows(){
    Grocery existing=manual("Rice",250.0,"g",false),purchased=manual("rice",100.0,"g",true);
    batch(new Ingredient("rice",0.5,"kg"),new Ingredient("RICE",250.0,"g"),new Ingredient("oil",1.0,"tbsp"),new Ingredient("oil",5.0,"ml"));
    List<Grocery> rows=db.dao().grocerySnapshot();assertEquals(3,rows.size());
    assertEquals(1000,rows.get(0).amount,0.001);assertEquals("Custom",rows.get(0).category);
    assertTrue(rows.get(1).checked);assertEquals(100,rows.get(1).amount,0.001);assertEquals(20,rows.get(2).amount,0.001);
    assertEquals(400,db.dao().plannedGrocerySnapshot().get(0).amount,0.001);
    db.dao().deleteMeal(meal);assertTrue(db.dao().plannedGrocerySnapshot().isEmpty());assertEquals(3,db.dao().grocerySnapshot().size());
    Recipe saved=db.dao().find(recipe.id);assertEquals(recipe.photoPaths(),saved.photoPaths());assertTrue(saved.favorite);
    assertEquals(Collections.singletonList(recipe.cookbook),db.dao().cookbookNamesForRecipe(recipe.id));
  }
  @Test public void manualCrudAndColumnOnlyChecksSurviveReopenAndConcurrentEdits(){
    Grocery original=manual("Original",10.0,"g",false),draft=new Grocery(original);
    db.dao().setGroceryChecked(original,true);draft.name="Edited";draft.amount=20.0;
    db.dao().saveManualGrocery(draft,5,15,240);assertTrue(db.dao().grocerySnapshot().get(0).checked);
    db.dao().setGroceryChecked(original,false);db.close();db=new LabDatabase(context,name);
    Grocery saved=db.dao().grocerySnapshot().get(0);assertEquals("Edited",saved.name);assertEquals(20,saved.amount,0.001);assertFalse(saved.checked);
    assertEquals(5,db.getWritableDatabase().getVersion());assertEquals(1,db.dao().mealSnapshot(null,null).size());
    db.dao().deleteGrocery(saved);assertTrue(db.dao().grocerySnapshot().isEmpty());assertNotNull(db.dao().find(recipe.id));
  }
  @Test public void stalePlannedCheckboxUsesItemKeyAfterRecomputation(){
    Grocery old=db.dao().plannedGrocerySnapshot().get(0);
    recipe.ingredients="100 g beans\n300 g rice";db.dao().update(recipe);db.dao().setGroceryChecked(old,true);
    Grocery rice=null,beans=null;for(Grocery g:db.dao().plannedGrocerySnapshot())if(g.name.equals("rice"))rice=g;else beans=g;
    assertNotNull(rice);assertTrue(rice.checked);assertFalse(beans.checked);assertEquals(600,rice.amount,0.001);
    recipe.ingredients="100 g beans";db.dao().update(recipe);
    assertThrows(IllegalStateException.class,()->db.dao().setGroceryChecked(old,true));
    assertFalse(db.dao().plannedGrocerySnapshot().get(0).checked);
  }
  @Test public void failedBatchRollsBackEarlierMergeAndNeverChangesOtherCollections(){
    manual("rice",100.0,"g",false);
    db.getWritableDatabase().execSQL("CREATE TEMP TRIGGER qa_batch_failure BEFORE INSERT ON groceries WHEN NEW.name='reject' BEGIN SELECT RAISE(ABORT,'QA rejection'); END");
    assertThrows(RuntimeException.class,()->batch(new Ingredient("rice",50.0,"g"),new Ingredient("reject",2.0,"g")));
    assertEquals(1,db.dao().grocerySnapshot().size());assertEquals(100,db.dao().grocerySnapshot().get(0).amount,0.001);
    assertThrows(IllegalArgumentException.class,()->batch(new Ingredient("rice",50.0,"g"),new Ingredient("bad",-1.0,"g")));
    assertEquals(100,db.dao().grocerySnapshot().get(0).amount,0.001);
    assertEquals(1,db.dao().mealSnapshot(null,null).size());assertEquals(recipe.photoPaths(),db.dao().find(recipe.id).photoPaths());
  }
  @Test public void unknownAmountsDeduplicateOnlyUnknownUncheckedCompatibleRows(){
    batch(new Ingredient("salt",null,""),new Ingredient("salt",null,""),new Ingredient("salt",2.0,"g"),new Ingredient("eggs",2.0,"quả"),new Ingredient("eggs",1.0,"củ"));
    assertEquals(4,db.dao().grocerySnapshot().size());assertNull(db.dao().grocerySnapshot().get(0).amount);
    Grocery old=db.dao().grocerySnapshot().get(0);db.dao().setGroceryChecked(old,true);batch(new Ingredient("salt",null,""));
    assertEquals(5,db.dao().grocerySnapshot().size());assertTrue(db.dao().grocerySnapshot().get(0).checked);
  }
}
