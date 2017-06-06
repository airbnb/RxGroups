package com.airbnb.rxgroups;

public class BaseObservableResubscriber {

  protected void setTag(AutoResubscribingObserver target, String tag) {
    target.setTag(tag);
  }

  protected void setTag(AutoTaggableObserver target, String tag) {
    target.setTag(tag);
  }

}
