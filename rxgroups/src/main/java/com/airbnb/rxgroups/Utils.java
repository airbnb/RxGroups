package com.airbnb.rxgroups;

import rx.Observer;

public class Utils {
  static String getObserverTag(Observer<?> observer) {
    if (observer instanceof TaggedObserver) {
      return ((TaggedObserver) observer).getTag();
    }
    return NonResubscribableTag.create(observer);
  }
}
