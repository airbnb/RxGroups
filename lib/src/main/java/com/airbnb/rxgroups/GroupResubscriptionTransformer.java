package com.airbnb.rxgroups;

import rx.Observable;

class GroupResubscriptionTransformer<T> implements Observable.Transformer<T, T> {
  private final ObservableGroup group;
  private final ManagedObservable<T> managedObservable;

  GroupResubscriptionTransformer(
      ObservableGroup group, ManagedObservable<T> managedObservable) {
    this.group = group;
    this.managedObservable = managedObservable;
  }

  @Override public Observable<T> call(Observable<T> observable) {
    return Observable.<T>create(subscriber ->
        group.resubscribe(managedObservable, observable, subscriber));
  }
}
