package com.airbnb.chimas;

import com.google.common.base.Preconditions;

import rx.Observable;
import rx.Observer;
import rx.Subscription;
import rx.functions.Action0;
import rx.subjects.ReplaySubject;

/**
 * This class is a middle man beween an {@link Observable} and an {@link Observer}. Since Retrofit
 * cancels upon unsubscription, this allows us to unsubscribe without cancelling the underlying
 * OkHttp call by using a proxy {@link ReplaySubject}. This is especially useful during activities
 * onPause() -> onResume(), where we want to avoid updating the UI but we still don't want to cancel
 * the request. This works like a lock/unlock events mechanism. It can be unlocked by calling
 * subscribe() again with the same Observable. Cancellation is usually more suited for lifecycle
 * events like Activity.onDestroy()
 */
final class SubscriptionProxy<T> implements RequestSubscription {
  private final ReplaySubject<T> replaySubject = ReplaySubject.create();
  private final Action0 onTerminate;
  private final Observable<T> upstream;
  private final Observer<T> proxy = new Observer<T>() {
    @Override public void onCompleted() {
      if (!finished) {
        observer.onCompleted();
        onTerminate.call();
        finished = true;
      }
    }

    @Override public void onError(Throwable e) {
      if (!finished) {
        observer.onError(e);
        onTerminate.call();
        finished = true;
      }
    }

    @Override public void onNext(T t) {
      if (!finished) {
        observer.onNext(t);
      }
    }
  };
  private Subscription upstreamSubscription;
  private Subscription downstreamSubscription;
  private Observer<T> observer;
  private boolean finished;

  private SubscriptionProxy(Observable<T> upstream, Action0 onTerminate) {
    this.onTerminate = onTerminate;
    this.upstream = upstream;
  }

  static <T> SubscriptionProxy<T> create(Observable<T> upstream, Action0 onTerminate) {
    return new SubscriptionProxy<>(upstream, onTerminate);
  }

  static <T> SubscriptionProxy<T> create(Observable<T> upstream) {
    return new SubscriptionProxy<>(upstream, DummyAction.instance());
  }

  void subscribe(Observer<T> observer) {
    if (this.observer != observer) {
      finished = false;
      this.observer = observer;
      upstreamSubscription = upstream.subscribe(replaySubject);
      downstreamSubscription = replaySubject.subscribe(proxy);
    } else if (isUnsubscribed()) {
      downstreamSubscription = replaySubject.subscribe(proxy);
    }
  }

  @Override public void cancel() {
    Preconditions.checkState(upstreamSubscription != null, "Must call subscribe() first");
    Preconditions.checkState(downstreamSubscription != null, "Must call subscribe() first");
    upstreamSubscription.unsubscribe();
    downstreamSubscription.unsubscribe();
  }

  @Override public void unsubscribe() {
    Preconditions.checkState(downstreamSubscription != null, "Must call subscribe() first");
    downstreamSubscription.unsubscribe();
  }

  @Override public boolean isUnsubscribed() {
    return downstreamSubscription.isUnsubscribed();
  }

  @Override public boolean isCancelled() {
    return downstreamSubscription.isUnsubscribed() && upstreamSubscription.isUnsubscribed();
  }
}