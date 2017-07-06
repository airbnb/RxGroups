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


import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.functions.Action;

/**
 * A wrapper for a {@link SubscriptionProxy} for use with the {@link ObservableGroup} to monitor a
 * subscription state by tag.
 */
class ManagedObservable<T> implements SourceSubscription {
  private final String observableTag;
  private final String observerTag;
  private final SubscriptionProxy<T> proxy;
  private boolean locked = true;
  private ObservableEmitter<? super T> observerEmitter;

  ManagedObservable(String observerTag, String observableTag, Observable<T> upstreamObservable,
      ObservableEmitter<? super T> observer, Action onTerminate) {
    this.observableTag = observableTag;
    this.observerTag = observerTag;
    this.observerEmitter = observer;
    proxy = SubscriptionProxy.create(upstreamObservable, onTerminate);
  }

  @Override public boolean isCancelled() {
    return proxy.isCancelled();
  }

  @Override public void cancel() {
    proxy.cancel();
    observerEmitter = null;
  }

  void lock() {
    locked = true;
    proxy.dispose();
  }

  @Override public void dispose() {
    proxy.dispose();
    observerEmitter = null;
  }

  @Override public boolean isDisposed() {
    return proxy.isDisposed();
  }

  void unlock() {
    locked = false;

    if (observerEmitter != null) {
      proxy.subscribe(observerEmitter);
    }
  }

  Observable<T> proxiedObservable() {
    return proxy.observable();
  }

  void resubscribe(ObservableEmitter<? super T> observerEmitter) {
    this.observerEmitter = Preconditions.checkNotNull(observerEmitter);

    if (!locked) {
      proxy.subscribe(observerEmitter);
    }
  }

  @Override
  public String toString() {
    return "ManagedObservable{" + "observableTag='" + observableTag + '\''
        + ", observerTag='" + observerTag + '\''
        + ", locked=" + locked
        + '}';
  }
}
