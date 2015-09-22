package com.airbnb.chimas;

import rx.Subscription;

public interface RequestSubscription extends Subscription {
  boolean isCancelled();
}
