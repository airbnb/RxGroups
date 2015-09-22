package com.airbnb.chimas;

import rx.Observer;

/** An {@link rx.Observer} that does nothing */
final class DummyObserver<T> implements Observer<T> {
  private static final DummyObserver INSTANCE = new DummyObserver();

  private DummyObserver() {
  }

  static <T> DummyObserver<T> instance() {
    //noinspection unchecked
    return INSTANCE;
  }

  @Override
  public void onCompleted() {
  }

  @Override
  public void onError(Throwable e) {
  }

  @Override
  public void onNext(T o) {
  }
}
