package com.example.recime.viewmodel;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

/** Keep mutation completion across view recreation; never retain an Activity. */
public final class DetailActionState extends ViewModel {
  public volatile boolean busy, aiBusy;
  public final MutableLiveData<String> aiOutcome = new MutableLiveData<>();
  public final MutableLiveData<String> outcome = new MutableLiveData<>();
}
