package com.airbnb.rxgroups;

import rx.Observer;

public interface TaggedObserver<T> extends Observer<T> {

  String getTag();

}
