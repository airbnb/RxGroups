package com.airbnb.chimas;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import rx.Observable;
import rx.Observer;
import rx.functions.Action0;

/**
 * A helper class for {@link RequestManager} that groups {@link Observable}s to be managed together.
 * For example, a fragment will probably want to group all of its requests together so it can
 * lock/unlock/clear them all at once according to its lifecycle. <p> Requests are added to a {@link
 * ObservableGroup} with an observer, and that observer will be called when a response is ready. If
 * the {@link ObservableGroup} is locked when the response arrives, or if the observer was removed,
 * the response will be queued and delivered when the {@link ObservableGroup} is unlocked and a
 * observer is added. <p> Only one instance of a tag can be tracked at once. If a duplicate tag is
 * added the original wil be canceled and discarded. This restriction allows observers to be
 * reattached to an observer without ambiguity.
 */
class ObservableGroup {
  private final Map<String, ManagedObservable<?>> groupMap = new ConcurrentHashMap<>();
  private boolean locked;

  /**
   * Adds a Observable to this group and executes (subscribes) to it. If an Observable with the same
   * tag is already added, the previous one will be canceled and removed before adding and
   * subscribing to the new one. Returns the new {@link RequestSubscription} object.
   */
  <T> RequestSubscription addAndExecute(
      final String tag, final Observable<T> observable, Observer<T> observer) {
    // noinspection unchecked
    ManagedObservable<T> previousObservable = (ManagedObservable<T>) groupMap.get(tag);

    if (previousObservable != null) {
      cancelAndRemove(previousObservable);
    }

    Action0 onTerminate = new Action0() {
      @Override
      public void call() {
        onTerminate(tag);
      }
    };

    ManagedObservable<T> managedObservable =
        new ManagedObservable<>(tag, observable, observer, onTerminate);

    groupMap.put(tag, managedObservable);

    if (!locked) {
      managedObservable.subscribe();
    }

    return managedObservable;
  }

  /**
   * Cancels all subscriptions and releases references to them. Queued events will be cleared and
   * their data lost.
   */
  void cleanUp() {
    for (ManagedObservable<?> managedObservable : groupMap.values()) {
      managedObservable.cancel();
    }
    groupMap.clear();
  }

  /**
   * Locks (prevents) Observables added to this group from emitting new events. Observables added
   * via {@code addAndExecute()} while the group is locked will **not** be subscribed until their
   * respective group is unlocked. If it's never unlocked, then the Observable will never be
   * subscribed to at all. This does not clear references to existing Observers. Please use {@link
   * #unsubscribe()} if you want to clear references to existing Observers.
   */
  void lock() {
    locked = true;
    for (ManagedObservable<?> managedObservable : groupMap.values()) {
      managedObservable.softUnsubscribe();
    }
  }

  /**
   * Unlocks (releases) Observables added to this group to emit new events until they are locked,
   * unsubscribed or cancelled.
   */
  void unlock() {
    locked = false;
    for (ManagedObservable<?> managedObservable : groupMap.values()) {
      managedObservable.subscribe();
    }
  }

  /**
   * Unsubscribes observers from this group. This does not cancel the underlying HTTP request. Also
   * clears any references to existing {@link Observer} in order to avoid leaks.
   */
  void unsubscribe() {
    for (ManagedObservable<?> managedObservable : groupMap.values()) {
      managedObservable.unsubscribe();
    }
  }

  /**
   * Resubscribes an Observer to an existing {@link ManagedObservable} for the provided tag. If the
   * Observable has already emitted events, they will be immediately delivered if it's unlocked.
   */
  @SuppressWarnings({"rawtypes", "unchecked"}) void resubscribe(String tag, Observer observer) {
    ManagedObservable<?> managedObservable = groupMap.get(tag);
    managedObservable.setObserverAndSubscribe(observer);
  }

  /**
   * Remove the Observable from being tracked by this group. No queued or future results will be
   * delivered. The subscription is also canceled to guarantee no responses will be delivered for it
   * again.
   */
  private void cancelAndRemove(ManagedObservable<?> managedObservable) {
    managedObservable.cancel();
    groupMap.remove(managedObservable.tag());
  }

  /**
   * Returns whether an {@link Observable} exists for the provided {@code tag}
   */
  boolean hasObservable(String tag) {
    ManagedObservable<?> managedObservable = groupMap.get(tag);
    return managedObservable != null;
  }

  /**
   * Removes any requests that match the provided request klass and requestId from the list of
   * managed requests. If no matching requests are found, it does nothing.
   */
  private void onTerminate(String tag) {
    ManagedObservable<?> managedObservable = groupMap.get(tag);
    if (managedObservable != null) {
      groupMap.remove(tag);
    }
  }
}
