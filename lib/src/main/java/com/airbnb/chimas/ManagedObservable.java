package com.airbnb.chimas;

import rx.Observable;
import rx.Observer;
import rx.functions.Action0;

/**
 * A wrapper for a {@link SubscriptionProxy} for use with the {@link ObservableGroup} to monitor a call's
 * state. Delegates all events to the provided delegate.
 */
class ManagedObservable<T> {
  private final String tag;
  private final SubscriptionProxy<T> proxy;
  private Observer<T> observer;

  ManagedObservable(
      String tag, Observable<T> observable, Observer<T> observer, Action0 onTerminate) {
    this.tag = tag;
    this.observer = observer;
    this.proxy = SubscriptionProxy.create(observable, onTerminate);
  }

  void cancel() {
    proxy.cancel();
  }

  void unsubscribe() {
    proxy.unsubscribe();
  }

  void setObserver(Observer<T> observer) {
    this.observer = observer;
  }

  void subscribe() {
    proxy.subscribe(observer);
  }

  RequestSubscription subscription() {
    return proxy;
  }

  boolean isCanceled() {
    return proxy.isCancelled();
  }

  String tag() {
    return tag;
  }
}