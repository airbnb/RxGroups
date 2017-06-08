package com.airbnb.rxgroups;

import rx.Observer;

public class Utils {
  static String getObserverTag(Observer<?> observer) {
    if (observer instanceof TaggedObserver) {
      String definedTag = ((TaggedObserver) observer).getTag();
      if (definedTag != null) {
        return definedTag;
      }
    }
    return NonResubscribableTag.create(observer);
  }
}
