package com.airbnb.chimas;

import rx.Observable;
import rx.Observer;
import rx.functions.Action0;

/**
 * A wrapper for a {@link SubscriptionProxy} for use with the {@link ObservableGroup} to monitor a
 * subscription state by tag.
 */
class ManagedObservable<T> implements RequestSubscription {
  private final String tag;
  private final SubscriptionProxy<T> proxy;
  private Observer<T> observer;

  ManagedObservable(
      String tag, Observable<T> observable, Observer<T> observer, Action0 onTerminate) {
    this.tag = tag;
    this.observer = observer;
    proxy = SubscriptionProxy.create(observable, onTerminate);
  }

  @Override public boolean isCancelled() {
    return proxy.isCancelled();
  }

  @Override public void cancel() {
    proxy.cancel();
    observer = null;
  }

  void lock() {
    proxy.unsubscribe();
  }

  @Override public void unsubscribe() {
    proxy.unsubscribe();
    observer = null;
  }

  @Override public boolean isUnsubscribed() {
    return proxy.isUnsubscribed();
  }

  void subscribe() {
    if (observer != null) {
      proxy.subscribe(observer);
    }
  }

  void subscribe(Observer<T> observer) {
    this.observer = Preconditions.checkNotNull(observer);
    proxy.subscribe(observer);
  }

  String tag() {
    return tag;
  }
}
