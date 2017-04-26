package com.airbnb.rxgroups;


import rx.Observer;

public abstract class AutoResubscribingObserver<T> implements Observer<T> {
    public String tag;

    @Override
    public void onCompleted() {

    }

    @Override
    public void onError(Throwable e) {

    }

    @Override
    public void onNext(T t) {

    }
}
