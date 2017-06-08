package com.airbnb.rxgroups;

import rx.Observer;

/**
 * An {@link Observer} which as a string "tag" which
 * uniquely identifies this Observer.
 */
public interface TaggedObserver<T> extends Observer<T> {

  /**
   * @return A string which uniquely identifies this Observer. In order to use
   * {@link ObservableGroup#resubscribe(TaggedObserver, String)} the tag must be
   * stable across lifecycles of the observer.
   */
  String getTag();

}
