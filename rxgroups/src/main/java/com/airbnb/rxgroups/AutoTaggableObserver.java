package com.airbnb.rxgroups;

import rx.Observer;

/**
 * {@link Observer} with a unique tag which can be automatically set during
 * {@link ObservableGroup#initializeAutoResubscription(Object)}
 * when used with {@link AutoResubscribe} or {@link AutoTag}.
 */
public interface AutoTaggableObserver<T> extends Observer<T> {
  void setTag(String tag);
}
