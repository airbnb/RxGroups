/*
 * Copyright (C) 2016 Airbnb, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.airbnb.rxgroups;

import rx.Observable;
import rx.Observer;
import rx.functions.Action0;

/**
 * A wrapper for a {@link SubscriptionProxy} for use with the {@link ObservableGroup} to monitor a
 * subscription state by tag.
 */
class ManagedObservable<T> implements RequestSubscription {
  private final String observableTag;
  private final String observerTag;
  private final SubscriptionProxy<T> proxy;
  private boolean locked = true;
  private Observable<T> observable;
  private Observer<? super T> observer;

  ManagedObservable(String observerTag, String observableTag, Observable<T> observable, Observer<? super T> observer,
                    Action0 onTerminate) {
    this.observableTag = observableTag;
    this.observerTag = observerTag;
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

  @Override
  public String toString() {
    return "ManagedObservable{" + "observableTag='" + observableTag + '\'' +
            ", observerTag='" + observerTag + '\'' +
            ", locked=" + locked +
            '}';
  }
}
