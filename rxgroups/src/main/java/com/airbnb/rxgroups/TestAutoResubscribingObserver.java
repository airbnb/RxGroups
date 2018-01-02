package com.airbnb.rxgroups;


import io.reactivex.annotations.NonNull;
import io.reactivex.disposables.Disposable;
import io.reactivex.observers.TestObserver;

class TestAutoResubscribingObserver extends AutoResubscribingObserver<String>
    implements Disposable {

  public final TestObserver<String> assertionTarget;

  TestAutoResubscribingObserver(String tag) {
    this.setTag(tag);
    assertionTarget = new TestObserver<>();
  }

  @Override public void onSubscribe(@NonNull Disposable d) {
    assertionTarget.onSubscribe(d);
  }

  @Override public void onComplete() {
    super.onComplete();
    assertionTarget.onComplete();
  }

  @Override public void onError(Throwable e) {
    super.onError(e);
    assertionTarget.onError(e);
  }

  @Override public void onNext(String s) {
    super.onNext(s);
    assertionTarget.onNext(s);
  }

  @Override public void dispose() {
    assertionTarget.dispose();
  }

  @Override public boolean isDisposed() {
    return assertionTarget.isDisposed();
  }
}
