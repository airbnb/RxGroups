package com.airbnb.rxgroups;

public class BaseObservableResubscriber {

  protected void setTag(AutoResubscribingObserver target, String tag) {
    target.setTag(tag);
  }

}
