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
import io.reactivex.Observer;
import io.reactivex.annotations.NonNull;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Action;
import io.reactivex.internal.functions.Functions;
import io.reactivex.observables.ConnectableObservable;
import io.reactivex.observers.DisposableObserver;

/**
 * This class is a middle man between an {@link Observable} and an {@link ObservableEmitter} or
 * {@link DisposableObserver}. The class allows the emitter to unsubscribe and resubscribe from the
 * source observable without terminating source observable. This works like a lock/unlock events
 * mechanism. This is useful for expensive operations, such as network requests, which should not be
 * cancelled even if a given Observer should be unsubscribed.
 * Cancellation is usually more suited for lifecycle events like Activity.onDestroy()
 */
final class SubscriptionProxy<T> {
  private final Observable<T> proxy;
  private final Disposable sourceDisposable;
  private final CompositeDisposable disposableList;
  private Disposable disposable;

  private SubscriptionProxy(Observable<T> sourceObservable, Action onTerminate) {
    final ConnectableObservable<T> replay = sourceObservable.replay();
    sourceDisposable = replay.connect();
    proxy = replay.doOnTerminate(onTerminate);
    disposableList = new CompositeDisposable(sourceDisposable);
  }

  static <T> SubscriptionProxy<T> create(Observable<T> observable, Action onTerminate) {
    return new SubscriptionProxy<>(observable, onTerminate);
  }

  static <T> SubscriptionProxy<T> create(Observable<T> observable) {
    return create(observable, Functions.EMPTY_ACTION);
  }

  @SuppressWarnings("unused") Disposable subscribe(Observer<? super T> observer) {
    dispose();
    disposable = proxy.subscribeWith(disposableWrapper(observer));
    disposableList.add(disposable);
    return disposable;
  }

  Disposable subscribe(ObservableEmitter<? super T> emitter) {
    dispose();
    disposable = proxy.subscribeWith(disposableWrapper(emitter));
    disposableList.add(disposable);
    return disposable;
  }

  void cancel() {
    disposableList.dispose();
  }

  void dispose() {
    if (disposable != null) {
      disposableList.remove(disposable);
    }
  }

  boolean isDisposed() {
    return disposable != null && disposable.isDisposed();
  }

  boolean isCancelled() {
    return isDisposed() && sourceDisposable.isDisposed();
  }

  Observable<T> observable() {
    return proxy;
  }

  DisposableObserver<? super T> disposableWrapper(final ObservableEmitter<? super T> emitter) {
    return new DisposableObserver<T>() {
      @Override public void onNext(@NonNull T t) {
        if (!emitter.isDisposed()) {
          emitter.onNext(t);
        }
      }

      @Override public void onError(@NonNull Throwable e) {
        if (!emitter.isDisposed()) {
          emitter.onError(e);
        }
      }

      @Override public void onComplete() {
        if (!emitter.isDisposed()) {
          emitter.onComplete();
        }
      }
    };
  }

  DisposableObserver<? super T> disposableWrapper(final Observer<? super T> observer) {
    return new DisposableObserver<T>() {
      @Override public void onNext(@NonNull T t) {
        observer.onNext(t);
      }

      @Override public void onError(@NonNull Throwable e) {
        observer.onError(e);
      }

      @Override public void onComplete() {
        observer.onComplete();
      }
    };
  }

}
