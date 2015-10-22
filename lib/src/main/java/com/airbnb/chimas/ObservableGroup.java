package com.airbnb.chimas;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import rx.Observable;
import rx.Observer;
import rx.functions.Action0;

/**
 * A helper class for {@link ObservableManager} that groups {@link Observable}s to be managed
 * together. For example, a fragment will probably want to group all of its requests together so it
 * can lock/unlock/clear them all at once according to its lifecycle. <p> Requests are added to a
 * {@link ObservableGroup} with an observer, and that observer will be called when a response is
 * ready. If the {@link ObservableGroup} is locked when the response arrives, or if the observer was
 * removed, the response will be queued and delivered when the {@link ObservableGroup} is unlocked
 * and a observer is added. <p> Only one instance of a tag can be tracked at once. If a duplicate
 * tag is added the original wil be canceled and discarded. This restriction allows observers to be
 * reattached to an observer without ambiguity.
 */
public final class ObservableGroup {
  private final Map<String, ManagedObservable<?>> groupMap = new ConcurrentHashMap<>();
  private final long groupId;
  private boolean locked;
  private boolean destroyed;

  public ObservableGroup(long groupId) {
    this.groupId = groupId;
  }

  public long id() {
    return groupId;
  }

  /**
   * Adds an {@link Observable} and {@link Observer} to this group and subscribes to it. If an
   * Observable with the same tag is already added, the previous one will be canceled and removed
   * before adding and subscribing to the new one. Returns the new {@link RequestSubscription}
   * object.
   */
  public <T> RequestSubscription add(String tag, Observable<T> observable, Observer<T> observer) {
    Preconditions.checkState(!destroyed);

    // noinspection unchecked
    ManagedObservable<T> previousObservable = (ManagedObservable<T>) groupMap.get(tag);

    if (previousObservable != null) {
      cancelAndRemove(previousObservable);
    }

    final String finalTag = tag;
    Action0 onTerminate = new Action0() {
      @Override
      public void call() {
        onTerminate(finalTag);
      }
    };

    ManagedObservable<T> managedObservable =
        new ManagedObservable<>(tag, observable, observer, onTerminate);

    groupMap.put(tag, managedObservable);

    if (!locked) {
      managedObservable.unlock();
    }

    return managedObservable;
  }

  /**
   * Checks if the given {@link Observable} has already been added to the group with {@link
   * #add(String, Observable, Observer)}. If so, it unsubscribes the previous Observer (if any) and
   * subscribes the existing Observable to the new provided Observer. This does not change the
   * locked status.
   */
  public <T> void addOrResubscribe(String tag, Observable<T> observable, Observer<T> observer) {
    if (hasObservable(tag)) {
      resubscribe(tag, observer);
    } else {
      add(tag, observable, observer);
    }
  }

  /**
   * Cancels all subscriptions and releases references to Observables and Observers. It is an error
   * to call {@link #add} once this is called.
   */
  void destroy() {
    destroyed = true;

    for (ManagedObservable<?> managedObservable : groupMap.values()) {
      managedObservable.cancel();
    }
    groupMap.clear();
  }

  /**
   * Locks (prevents) Observables added to this group from emitting new events. Observables added
   * via {@code add()} while the group is locked will **not** be subscribed until their respective
   * group is unlocked. If it's never unlocked, then the Observable will never be subscribed to at
   * all. This does not clear references to existing Observers. Please use {@link #unsubscribe()} if
   * you want to clear references to existing Observers.
   */
  public void lock() {
    locked = true;
    for (ManagedObservable<?> managedObservable : groupMap.values()) {
      managedObservable.lock();
    }
  }

  /**
   * Unlocks (releases) Observables added to this group to emit new events until they are locked,
   * unsubscribed or cancelled.
   */
  public void unlock() {
    locked = false;
    for (ManagedObservable<?> managedObservable : groupMap.values()) {
      managedObservable.unlock();
    }
  }

  /**
   * Unsubscribes all Observers managed by this group. Also clears any references to existing {@link
   * Observer} objects in order to avoid leaks. This does not disconnect from the upstream
   * Observable, so it can be resumed upon calling {@link #resubscribe(String, Observer)} if
   * needed.
   */
  public void unsubscribe() {
    for (ManagedObservable<?> managedObservable : groupMap.values()) {
      managedObservable.unsubscribe();
    }
  }

  /**
   * Resubscribes an Observer to an existing {@link Observable} for the provided tag. If the
   * Observable has already emitted events, they will be immediately delivered if it's unlocked. Any
   * previously subscribed Observers will be unsubscribed before the new one.
   */
  @SuppressWarnings({"rawtypes", "unchecked"})
  public void resubscribe(String tag, Observer observer) {
    ManagedObservable<?> managedObservable = groupMap.get(tag);
    managedObservable.subscribe(observer);
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

  /** Returns whether an {@link Observable} exists for the provided {@code tag} */
  public boolean hasObservable(String tag) {
    ManagedObservable<?> managedObservable = groupMap.get(tag);
    return managedObservable != null;
  }

  /** Removes any Observables that match the provided tag. If none are found, it does nothing. */
  private void onTerminate(String tag) {
    ManagedObservable<?> managedObservable = groupMap.get(tag);
    if (managedObservable != null) {
      groupMap.remove(tag);
    }
  }
}
