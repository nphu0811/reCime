package com.example.recime.viewmodel;
import androidx.lifecycle.*;
public class CookbookState extends ViewModel {
  public volatile boolean busy;
  public final MutableLiveData<String> outcome = new MutableLiveData<>();
  public static final class Members {
    public final java.util.ArrayList<Long> ids = new java.util.ArrayList<>();
    public final java.util.ArrayList<String> titles = new java.util.ArrayList<>();
    public java.util.Set<Long> selected;
    public String error;
  }
  public final MutableLiveData<Members> members = new MutableLiveData<>();
  private boolean loading;
  public void load(com.example.recime.sql.RecipeRepository repo, String book) {
    if (loading || members.getValue() != null) return;
    loading = true;
    repo.io.execute(() -> {
      Members next = new Members();
      try {
        next.selected = repo.dao.recipeIdsForCookbook(book);
        for (com.example.recime.model.Recipe recipe : repo.dao.recipeSnapshot()) {
          if (com.example.recime.helper.LibraryScope.includes(recipe, false)) {
            next.ids.add(recipe.id); next.titles.add(recipe.title);
          }
        }
      } catch (Exception error) { next.error = "Không tải được thư viện; đóng rồi mở lại để thử lại"; }
      members.postValue(next);
    });
  }
}
