package com.example.recime;

import com.example.recime.model.Recipe;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class RecipePhotoModelTest {
  @Test public void legacyCoverRemainsUntilExplicitlyRemoved() {
    Recipe recipe = new Recipe(); recipe.image = "legacy-cover";
    assertEquals(Collections.singletonList("legacy-cover"), recipe.photoPaths());
    recipe.setPhotoPaths(Collections.emptyList());
    assertEquals("", recipe.image);
    assertTrue(recipe.photoPaths().isEmpty());
  }

  @Test public void fivePhotosKeepOrderAndDetachedDraftCannotMutateSavedSelection() {
    Recipe recipe = new Recipe();
    List<String> paths = new ArrayList<>(Arrays.asList("one", "two", "three", "four", "five"));
    recipe.setPhotoPaths(paths); paths.clear();
    Recipe draft = new Recipe(recipe); draft.images.remove(0);
    assertEquals(5, recipe.photoPaths().size());
    assertEquals("one", recipe.image);
    assertEquals("five", recipe.photoPaths().get(4));
  }

  @Test public void sixthPhotoIsRejectedWithoutChangingOldCover() {
    Recipe recipe = new Recipe(); recipe.image = "old";
    try {
      recipe.setPhotoPaths(Arrays.asList("1", "2", "3", "4", "5", "6"));
      fail("Six photos must be rejected");
    } catch (IllegalArgumentException expected) {
      assertEquals(Collections.singletonList("old"), recipe.photoPaths());
    }
  }
}
