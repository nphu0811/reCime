package com.example.recime.viewmodel;
import com.example.recime.helper.GroceryBatchDraft;
public class GroceryBatchState extends GroceryMutationState {
  public final GroceryBatchDraft draft=new GroceryBatchDraft();
  public int stage,sourceMode,entryIndex;
  public String query="";
}
