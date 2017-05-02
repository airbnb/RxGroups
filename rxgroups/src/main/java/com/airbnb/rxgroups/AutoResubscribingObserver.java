package com.airbnb.rxgroups;


import rx.Observer;

public abstract class AutoResubscribingObserver<T> implements Observer<T> {

  private String tag;

  public String getTag() {
    return tag;
  }

  /**
   * Sets this observer's resubscription {@code tag}.
   * This is automatically done when using the {@link AutoResubscribe} annotation.
   */
  public void setTag(String tag) {
    this.tag = tag;
  }

  @Override
  public void onCompleted() {

  }

  @Override
  public void onError(Throwable e) {

  }

  @Override
  public void onNext(T t) {

  }

}
