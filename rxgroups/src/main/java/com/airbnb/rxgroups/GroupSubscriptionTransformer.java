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

import rx.AsyncEmitter;
import rx.Observable;
import rx.Observer;
import rx.functions.Action1;

/**
 * Transforms an existing {@link Observable} by returning a new {@link Observable} that is
 * automatically added to the provided {@link ObservableGroup} with the specified {@code tag} when
 * subscribed to.
 */
class GroupSubscriptionTransformer<T> implements Observable.Transformer<T, T> {
  private final ObservableGroup group;
  private final String tag;

  GroupSubscriptionTransformer(ObservableGroup group, String tag) {
    this.group = group;
    this.tag = tag;
  }

  @Override public Observable<T> call(final Observable<T> observable) {
    return Observable.fromEmitter(new Action1<AsyncEmitter<T>>() {
      @Override public void call(final AsyncEmitter<T> emitter) {
        group.add(tag, observable, new Observer<T>() {
          @Override public void onCompleted() {
            emitter.onCompleted();
          }

          @Override public void onError(Throwable e) {
            emitter.onError(e);
          }

          @Override public void onNext(T t) {
            emitter.onNext(t);
          }
        });
      }
    }, AsyncEmitter.BackpressureMode.BUFFER);
  }
}
