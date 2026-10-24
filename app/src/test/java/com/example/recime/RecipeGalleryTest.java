package com.example.recime;
import static org.junit.Assert.*;
import com.example.recime.helper.RecipeGallery;
import com.example.recime.model.Recipe;
import java.util.*;
import org.junit.Test;

public class RecipeGalleryTest {
  @Test public void orderedPhotosClampAndRestoreSelectedPath() {
    Recipe r=new Recipe(); r.setPhotoPaths(Arrays.asList("a","b","c","d","e"));
    RecipeGallery g=new RecipeGallery();g.update(r);
    assertEquals("a",g.path());g.move(20);assertEquals("e",g.path());
    RecipeGallery restored=new RecipeGallery();restored.restore(g.index(),g.path());restored.update(r);
    assertEquals(4,restored.index());assertEquals("e",restored.path());
    r.setPhotoPaths(Arrays.asList("e","b"));restored.update(r);
    assertEquals(0,restored.index());assertEquals("e",restored.path());
  }
  @Test public void removalClampsSelectionAndEmptyClearsIt() {
    Recipe r=new Recipe();r.setPhotoPaths(Arrays.asList("a","b","c"));
    RecipeGallery g=new RecipeGallery();g.update(r);g.move(2);
    r.setPhotoPaths(Arrays.asList("a","b"));g.update(r);assertEquals("b",g.path());
    r.setPhotoPaths(Collections.emptyList());g.update(r);g.move(-1);
    assertEquals(0,g.count());assertEquals("",g.path());assertEquals(0,g.index());
  }
  @Test public void legacyCoverAndDefensiveMaximum() {
    Recipe r=new Recipe();r.image="legacy";RecipeGallery g=new RecipeGallery();g.update(r);
    assertEquals(1,g.count());assertEquals("legacy",g.path());g.move(-1);assertEquals(0,g.index());
    r.images=Arrays.asList("a","b","c","d","e","overflow");g.update(r);
    assertEquals(5,g.count());g.move(100);assertEquals("e",g.path());
    assertEquals(6,r.images.size()); // Display bounds never alter persisted data.
  }
}
