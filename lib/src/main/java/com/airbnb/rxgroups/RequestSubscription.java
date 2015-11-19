package com.airbnb.rxgroups;

import rx.Subscriber;
import rx.Subscription;

public interface RequestSubscription extends Subscription {
  /**
   * Indicates whether this {@code RequestSubscription} is currently cancelled, that is, whether the
   * underlying HTTP request associated to it has been cancelled.
   *
   * @return {@code true} if this {@code Subscription} is currently cancelled, {@code false}
   * otherwise
   */
  boolean isCancelled();

  /**
   * Stops the receipt of notifications on the {@link Subscriber} that was registered when this
   * Subscription was received. This allows unregistering an {@link Subscriber} before it has
   * finished receiving all events (i.e. before onCompleted is called). Also causes the underlying
   * HTTP request to be cancelled by unsubscribing from Retrofit's Observable.
   */
  void cancel();
}
