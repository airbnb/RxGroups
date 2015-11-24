package com.airbnb.rxgroups;

import rx.Observable;
import rx.Observer;

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

  @Override public Observable<T> call(Observable<T> observable) {
    return Observable.<T>create(subscriber -> group.add(tag, observable, new Observer<T>() {
      @Override public void onCompleted() {
        if (!subscriber.isUnsubscribed()) {
          subscriber.onCompleted();
        }
      }

      @Override public void onError(Throwable e) {
        if (!subscriber.isUnsubscribed()) {
          subscriber.onError(e);
        }
      }

      @Override public void onNext(T t) {
        if (!subscriber.isUnsubscribed()) {
          subscriber.onNext(t);
        }
      }
    }));
  }
}
