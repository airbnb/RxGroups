package com.airbnb.rxgroups;


import rx.Observer;

public abstract class AutoResubscribingObserver<T> implements Observer<T> {
    String tag;
}
