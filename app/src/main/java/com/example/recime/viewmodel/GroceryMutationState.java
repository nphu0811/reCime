package com.example.recime.viewmodel;
import androidx.lifecycle.*;
public class GroceryMutationState extends ViewModel {
  public volatile boolean busy;
  public final MutableLiveData<String> outcome=new MutableLiveData<>();
}
