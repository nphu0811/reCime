package com.example.recime;
import static org.junit.Assert.*;
import com.example.recime.helper.*;
import com.example.recime.model.*;
import java.util.*;
import org.junit.Test;
public class GroceryDomainTest {
  private Recipe recipe(){Recipe r=new Recipe();r.servings=2;r.ingredients="200 g gạo\n1 tbsp dầu";return r;}
  @Test public void selectedIngredientsScaleWithoutMutatingRecipe(){
    Recipe r=recipe();GroceryBatchDraft draft=new GroceryBatchDraft();draft.select("r:1","Recipe",r,4);
    GroceryBatchDraft.Entry entry=draft.entries.get("r:1");entry.selected[1]=false;
    assertEquals(1,draft.collect().size());assertEquals(400,draft.collect().get(0).amount,0.001);
    r.ingredients="Different";assertEquals(400,draft.collect().get(0).amount,0.001);assertEquals(2,r.servings,0.001);
  }
  @Test public void sourceIdentityDistinguishesMealOccurrencesAndDoesNotDoubleSelect(){
    GroceryBatchDraft draft=new GroceryBatchDraft();Recipe r=recipe();
    draft.select("m:1","Meal1",r,4);draft.select("m:1","Meal1",r,4);draft.select("m:2","Meal2",r,1);
    assertEquals(2,draft.entries.size());assertEquals(500,GroceryAggregator.merge(draft.collect(),5,15,240).get(0).amount,0.001);
  }
  @Test public void invalidServingsEmptySelectionAndOverflowCannotCommit(){
    GroceryBatchDraft draft=new GroceryBatchDraft();assertThrows(IllegalArgumentException.class,draft::collect);
    draft.select("r","Recipe",recipe(),0);GroceryBatchDraft.Entry entry=draft.entries.get("r");
    entry.servings="0";assertThrows(IllegalArgumentException.class,draft::collect);
    entry.servings="NaN";assertThrows(IllegalArgumentException.class,draft::collect);
    entry.servings="2";Arrays.fill(entry.selected,false);assertThrows(IllegalArgumentException.class,draft::collect);
    assertThrows(IllegalArgumentException.class,()->GroceryAggregator.merge(Arrays.asList(new Ingredient("rice",Double.MAX_VALUE,"kg")),5,15,240));
  }
  @Test public void groupingAndChecklistRetainCheckedSourceAndUnknownQuantity(){
    Grocery manual=new Grocery();manual.name="Gạo";manual.amount=500.0;manual.unit="g";manual.category="Khô";manual.checked=true;
    Grocery planned=new Grocery();planned.name="Muối";planned.planned=true;planned.category="Khô";
    List<Grocery> input=Arrays.asList(planned,manual);List<BoardRow> rows=GroceryPresentation.rows(input,2);
    assertTrue(rows.get(0).isHeader());assertEquals(manual,rows.get(1).grocery);
    String text=GroceryPresentation.checklist(rows);assertTrue(text.contains("[x] Gạo"));assertTrue(text.contains("[ ] Muối"));
    assertTrue(text.contains("500 g"));assertTrue(text.contains("Tùy lượng"));assertTrue(text.contains("Meal Planner"));
    assertEquals(planned,input.get(0));assertTrue(GroceryPresentation.rows(Collections.emptyList(),0).isEmpty());
    assertEquals(planned,GroceryPresentation.rows(input,3).get(1).grocery);
  }
  @Test public void incompatibleUnitsAndUnknownAmountsAreNotInvented(){
    List<Ingredient> values=GroceryAggregator.merge(Arrays.asList(new Ingredient("gạo",0.5,"kg"),new Ingredient("GẠO",250.0,"g"),new Ingredient("gạo",1.0,"cup"),new Ingredient("muối",null,"")),5,15,240);
    assertEquals(3,values.size());assertEquals(750,values.get(0).amount,0.001);assertEquals(240,values.get(1).amount,0.001);assertNull(values.get(2).amount);
    assertThrows(IllegalArgumentException.class,()->GroceryAggregator.merge(values,Double.NaN,15,240));
  }
}
