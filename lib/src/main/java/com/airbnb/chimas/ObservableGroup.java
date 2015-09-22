package com.airbnb.chimas;

import android.support.v4.util.ArrayMap;
import android.util.Log;

import java.util.Map;

import rx.Observable;
import rx.Observer;
import rx.Subscription;
import rx.functions.Action0;

/**
 * A helper class for {@link RequestManager} that groups requests to be managed together. For
 * example, a fragment will probably want to group all of its requests together so it can
 * lock/unlock/clear them all at once according to its lifecycle.
 * <p/>
 * Requests are added to a {@link ObservableGroup} with a observer, and that observer will be called when
 * a response is ready. If the {@link ObservableGroup} is locked when the response arrives, or if the
 * observer was removed, the response will be queued and delivered when the {@link ObservableGroup} is
 * unlocked and a observer is added.
 * <p/>
 * Only one instance of a request class can be tracked at once. If a duplicate request is added the
 * original wil be canceled and discarded. This restriction allows listeners to be reattached to a
 * request without ambiguity.
 */
class ObservableGroup {
  private static final String TAG = "CallGroup";

  private final Map<String, ManagedObservable<?>> requestMap = new ArrayMap<>();
  private boolean isLocked;

  /**
   * Adds a call to this group and executes (subscribes) to it. If a call of the same class is
   * already added, the previous one will be canceled and removed before adding and subscribing to
   * the new one. Returns the new {@link rx.Subscription} object.
   */
  <T> Subscription addAndExecute(
      final String tag, final Observable<T> observable, Observer<T> observer) {
    ManagedObservable previousRequest = requestMap.get(tag);

    if (previousRequest != null) {
      cancelAndRemoveRequest(previousRequest);
    }

    observable.doOnTerminate(new Action0() {
      @Override
      public void call() {
        Log.d(TAG, "doOnTerminate() -> request tag " + tag);
        onTerminate(tag);
      }
    });

    ManagedObservable<T> managedObservable = new ManagedObservable<>(tag, observable, observer);
    requestMap.put(tag, managedObservable);

    if (!isLocked) {
      managedObservable.subscribe();
    }

    return managedObservable.subscription();
  }

  /**
   * Cancels all requests and releases references to them. Queued responses will be cleared and
   * their results lost.
   */
  void cleanUp() {
    for (ManagedObservable managedObservable : requestMap.values()) {
      managedObservable.cancel();
    }
    requestMap.clear();
  }

  /**
   * Locks (prevents) request Observables added to this group from emitting new events. Requests
   * added via {@code addAndExecute()} while the group is locked will **not** be fired to the
   * network until their respective group is unlocked. If it's never unlocked, then the request
   * won't be executed at all.
   */
  void lock() {
    isLocked = true;
    unsubscribe();
  }

  /**
   * Unlocks (releases) request Observables added to this group to emit new events until they are
   * locked, unsubscribed or cancelled.
   */
  void unlock() {
    isLocked = false;
    resubscribeAll();
  }

  private void resubscribeAll() {
    for (ManagedObservable managedObservable : requestMap.values()) {
      managedObservable.subscribe();
    }
  }

  /** Unsubscribes observers from this group. This does not cancel the underlying HTTP request. */
  void unsubscribe() {
    for (ManagedObservable managedObservable : requestMap.values()) {
      managedObservable.unsubscribe();
    }
  }

  /**
   * Resubscribes an Observer to an existing {@link ManagedObservable} for the provided requestClass. If
   * the request already has a response, it will be immediately delivered by the Observable if
   * it's unlocked.
   */
  void resubscribe(String tag, Observer observer) {
    //noinspection unchecked
    ManagedObservable<?> managedObservable = requestMap.get(tag);
    //noinspection unchecked
    managedObservable.setObserver(observer);
    managedObservable.subscribe();
  }

  /**
   * Remove the request from being tracked by this group. No queued or future results will be
   * delivered. The request is also canceled to guarantee no responses will be delivered for it
   * again.
   */
  private void cancelAndRemoveRequest(ManagedObservable managedObservable) {
    managedObservable.cancel();
    requestMap.remove(managedObservable.tag());
  }

  /**
   * Returns a request call that has been previously added. Returns null if that request doesn't
   * exist, either because it was never added, it finished, or it was removed manually.
   */
  boolean hasObservable(String tag) {
    ManagedObservable managedObservable = requestMap.get(tag);
    return managedObservable != null;
  }

  /**
   * Removes any requests that match the provided request klass and requestId from the list of
   * managed requests. If no matching requests are found, it does nothing.
   */
  private void onTerminate(String tag) {
    ManagedObservable managedObservable = requestMap.get(tag);
    if (managedObservable != null) {
      requestMap.remove(tag);
    }
  }
}