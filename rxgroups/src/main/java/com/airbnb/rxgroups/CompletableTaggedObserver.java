package com.airbnb.rxgroups;

import io.reactivex.CompletableObserver;

public interface CompletableTaggedObserver<T> extends CompletableObserver {
    String getTag();
}
