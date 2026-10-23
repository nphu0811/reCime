package com.example.recime;
import static org.junit.Assert.*;
import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.recime.sql.LabDatabase;
import com.example.recime.model.Recipe;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
@RunWith(AndroidJUnit4.class)
public class CookbookSelectionTest {
  @Test public void selectionsRenameDeleteAndReopenPreserveOtherMembershipsAndCatalog(){
    Context c=ApplicationProvider.getApplicationContext();String name="home-books-"+System.nanoTime()+".db";
    LabDatabase db=new LabDatabase(c,name);
    try{
      db.dao().createCookbook("One");db.dao().createCookbook("Two");
      Recipe r=new Recipe();r.title="Personal";r.cookbook="One";r.image="legacy.jpg";r.ingredients="100 g rice";r.id=db.dao().insert(r);
      db.dao().addRecipeToCookbook(r.id,"Two");
      Recipe catalog=new Recipe();catalog.title="Catalog";catalog.catalogKey="test:catalog";catalog.cookbook="One";catalog.id=db.dao().insert(catalog);
      db.dao().selectCookbookRecipes("One",Collections.emptySet());
      assertFalse(db.dao().recipeIdsForCookbook("One").contains(r.id));assertTrue(db.dao().recipeIdsForCookbook("Two").contains(r.id));
      assertTrue(db.dao().recipeIdsForCookbook("One").contains(catalog.id));
      db.dao().selectCookbookRecipes("One",Collections.singleton(r.id));
      db.dao().renameCookbook("One","Renamed");
      assertTrue(db.dao().recipeIdsForCookbook("Renamed").contains(r.id));
      db.dao().deleteCookbook("Renamed");
      assertNotNull(db.dao().find(r.id));assertNotNull(db.dao().find(catalog.id));
      assertTrue(db.dao().recipeIdsForCookbook("Two").contains(r.id));
      assertEquals("legacy.jpg",db.dao().find(r.id).image);assertEquals("test:catalog",db.dao().find(catalog.id).catalogKey);
      db.close();db=new LabDatabase(c,name);assertEquals(5,db.getReadableDatabase().getVersion());
      assertNotNull(db.dao().find(r.id));assertTrue(db.dao().recipeIdsForCookbook("Two").contains(r.id));
    }finally{db.close();c.deleteDatabase(name);}
  }
  @Test public void invalidSelectionIsAtomicAndDoesNotChangeMemberships(){
    Context c=ApplicationProvider.getApplicationContext();String name="home-invalid-"+System.nanoTime()+".db";LabDatabase db=new LabDatabase(c,name);
    try{
      Recipe r=new Recipe();r.title="Personal";r.cookbook="One";r.id=db.dao().insert(r);
      Set<Long> selection=new HashSet<>();selection.add(r.id);selection.add(Long.MAX_VALUE);
      try{db.dao().selectCookbookRecipes("One",selection);fail("missing recipe");}catch(IllegalArgumentException expected){}
      assertTrue(db.dao().recipeIdsForCookbook("One").contains(r.id));assertEquals("One",db.dao().find(r.id).cookbook);
    }finally{db.close();c.deleteDatabase(name);}
  }
}
