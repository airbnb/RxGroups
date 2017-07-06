package com.airbnb.rxgroups;

import io.reactivex.annotations.NonNull;
import io.reactivex.disposables.Disposable;

/**
 * A {@link io.reactivex.Observer} which has a stable tag. Must be used with {@link AutoResubscribe}
 * annotation to set the tag before observer is used.
 */
public abstract class AutoResubscribingObserver<T> implements TaggedObserver<T> {

  private String tag;

  public final String getTag() {
    return tag;
  }

  void setTag(String tag) {
    this.tag = tag;
  }

  @Override
  public void onComplete() {

  }

  @Override
  public void onError(Throwable e) {

  }

  @Override
  public void onNext(T t) {

  }

  @Override public void onSubscribe(@NonNull Disposable d) {

  }
}
