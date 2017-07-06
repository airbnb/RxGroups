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

import io.reactivex.disposables.Disposable;

public interface SourceSubscription extends Disposable {
  /**
   * Indicates whether this {@code SourceSubscription} is currently cancelled, that is, whether the
   * underlying HTTP request associated to it has been cancelled.
   *
   * @return {@code true} if this {@code Subscription} is currently cancelled, {@code false}
   * otherwise
   */
  boolean isCancelled();

  /**
   * Stops the receipt of notifications on the {@link io.reactivex.Observer} that was registered
   * when this Subscription was received. This allows unregistering an {@link io.reactivex.Observer}
   * before it has finished receiving all events (i.e. before onCompleted is called). Also causes
   * the underlying HTTP request to be cancelled by unsubscribing from Retrofit's Observable.
   */
  void cancel();
}
