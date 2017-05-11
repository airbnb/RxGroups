package com.airbnb.rxgroups;


public abstract class AutoResubscribingObserver<T> implements TaggedObserver<T> {

  private String tag;

  public final String getTag() {
    return tag;
  }

  void setTag(String tag) {
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
