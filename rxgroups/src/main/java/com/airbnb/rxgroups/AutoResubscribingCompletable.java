package com.airbnb.rxgroups;

import io.reactivex.annotations.NonNull;
import io.reactivex.disposables.Disposable;

public abstract class AutoResubscribingCompletable<T> implements CompletableTaggedObserver<T> {

    private String tag;

    public final String getTag() {
        return tag;
    }

    void setTag(String tag) {
        this.tag = tag;
    }

    @Override
    public void onComplete() {

    }

    @Override
    public void onError(Throwable e) {

    }

    @Override
    public void onSubscribe(@NonNull Disposable d) {

    }
}
