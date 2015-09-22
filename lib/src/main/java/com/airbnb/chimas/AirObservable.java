package com.airbnb.chimas;

import rx.Observable;
import rx.Subscriber;

public final class AirObservable<T> extends Observable<T> {
  private final AirRequest airRequest;

  AirObservable(AirRequest airRequest, OnSubscribe<T> onSubscribe) {
    super(onSubscribe);
    this.airRequest = airRequest;
  }

  static <T> AirObservable<T> create(final AirRequest airRequest, Observable<T> original) {
    return (AirObservable<T>) original.compose(new Transformer<T, T>() {
      @Override public Observable<T> call(final Observable<T> observable) {
        return new AirObservable<>(airRequest, new OnSubscribe<T>() {
          @Override public void call(Subscriber<? super T> subscriber) {
            observable.subscribe(subscriber);
          }
        });
      }
    });
  }
}
