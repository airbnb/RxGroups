package com.airbnb.rxgroups;


import io.reactivex.Observer;

final class Utils {
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
