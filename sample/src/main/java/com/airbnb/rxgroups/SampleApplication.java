package com.airbnb.rxgroups;

import android.app.Application;

import java.util.concurrent.TimeUnit;

import rx.Observable;

public class SampleApplication extends Application {
  private ObservableManager observableManager;
  private final Observable<Long> timerObservable = Observable.interval(1, 1, TimeUnit.SECONDS);

  @Override public void onCreate() {
    super.onCreate();
    observableManager = new ObservableManager();
  }

  public ObservableManager observableManager() {
    return observableManager;
  }

  public Observable<Long> timerObservable() {
    return timerObservable;
  }
}
