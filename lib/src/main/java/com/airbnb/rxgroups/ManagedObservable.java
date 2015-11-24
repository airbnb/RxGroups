package com.airbnb.rxgroups;

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
  private boolean locked = true;
  private Observable<T> observable;
  private Observer<? super T> observer;

  ManagedObservable(String tag, Observable<T> observable, Observer<? super T> observer,
      Action0 onTerminate) {
    this.tag = tag;
    this.observer = observer;
    proxy = SubscriptionProxy.create(observable, onTerminate);
    this.observable = proxy.observable();
  }

  @Override public boolean isCancelled() {
    return proxy.isCancelled();
  }

  @Override public void cancel() {
    proxy.cancel();
    observer = null;
  }

  void lock() {
    locked = true;
    proxy.unsubscribe();
  }

  @Override public void unsubscribe() {
    proxy.unsubscribe();
    observer = null;
  }

  @Override public boolean isUnsubscribed() {
    return proxy.isUnsubscribed();
  }

  void unlock() {
    locked = false;

    if (observer != null) {
      proxy.subscribe(observable, observer);
    }
  }

  Observable<T> observable() {
    return proxy.observable();
  }

  void resubscribe(Observable<T> observable, Observer<? super T> observer) {
    this.observable = observable;
    this.observer = Preconditions.checkNotNull(observer);

    if (!locked) {
      proxy.subscribe(observable, observer);
    }
  }

  String tag() {
    return tag;
  }

  @Override public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ManagedObservable<?> that = (ManagedObservable<?>) o;

    if (!tag.equals(that.tag)) return false;
    //noinspection SimplifiableIfStatement
    if (!proxy.equals(that.proxy)) return false;
    return !(observer != null ? !observer.equals(that.observer) : that.observer != null);
  }

  @Override public int hashCode() {
    int result = tag.hashCode();
    result = 31 * result + proxy.hashCode();
    result = 31 * result + (observer != null ? observer.hashCode() : 0);
    return result;
  }

  @Override public String toString() {
    return "ManagedObservable{"
        + "tag='" + tag + '\''
        + '}';
  }
}
