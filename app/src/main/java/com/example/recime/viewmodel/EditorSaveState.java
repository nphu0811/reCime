package com.example.recime.viewmodel;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

/** Fragment-scoped save outcome; contains no Activity or View references. */
public class EditorSaveState extends ViewModel {
  public volatile boolean saving;
  public final MutableLiveData<String> outcome = new MutableLiveData<>();
}
