package com.airbnb.rxgroups.android;

import rx.Scheduler;
import rx.android.plugins.RxAndroidSchedulersHook;
import rx.schedulers.Schedulers;

class TestRxAndroidSchedulersHook extends RxAndroidSchedulersHook {
  @Override public Scheduler getMainThreadScheduler() {
    return Schedulers.immediate();
  }
}
