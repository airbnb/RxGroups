package com.airbnb.rxgroups;

import rx.Observer;

public interface ResubscriptionObserver<T> extends Observer<T> {
  Object resubscriptionTag();
}
