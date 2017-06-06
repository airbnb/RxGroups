package com.airbnb.rxgroups;

import rx.Observer;

public interface TaggedObserver<T> extends Observer<T> {

  /**
   * @return A string which uniquely identifies this Observer. In order to use
   * {@link ObservableGroup#resubscribe(TaggedObserver, String)} the tag must be
   * stable across lifecycles of the observer.
   */
  String getTag();

}
