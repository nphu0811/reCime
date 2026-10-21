package com.example.recime.ui;

import android.app.Application;
import androidx.lifecycle.*;
import com.example.recime.data.*;
import java.util.List;

public class LabViewModel extends AndroidViewModel {
  public final RecipeRepository repository;
  public final LiveData<List<Recipe>> recipes;
  public final LiveData<List<Meal>> meals;
  public final LiveData<List<Grocery>> groceries;

  public LabViewModel(Application app) {
    super(app);
    repository = RecipeRepository.get(app);
    recipes = repository.dao.recipes();
    meals = repository.dao.meals();
    groceries = repository.dao.groceries();
  }
}
