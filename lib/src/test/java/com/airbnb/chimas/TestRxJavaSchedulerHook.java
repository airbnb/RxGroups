package com.airbnb.chimas;

import rx.Scheduler;
import rx.plugins.RxJavaSchedulersHook;
import rx.schedulers.Schedulers;

public class TestRxJavaSchedulerHook extends RxJavaSchedulersHook {
  @Override public Scheduler getIOScheduler() {
    return Schedulers.immediate();
  }
}
