package com.airbnb.chimas;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import rx.Observable;
import rx.Observer;

/**
 * Easily keep reference to {@link Observable}s across lifecycle changes. Observables are grouped by
 * a unique group id, which allows you to manage and reclaim subscriptions made with the same id.
 * Subscribe to observables, and then lock or unlock their observers to control when you get the
 * event back. Events will be held in a queue until an Observer is added and the group is unlocked.
 */
public final class RequestManager {

  /** Map ids to a group of observables. */
  private final Map<Integer, ObservableGroup> observableGroupMap = new ConcurrentHashMap<>();

  /**
   * Fires the provided Observable and saves it under the given id. If an Observable with the same
   * tag is already running, the previous one will be canceled and removed from this manager.
   */
  public <T> RequestSubscription execute(
      int groupId, String tag, Observable<T> observable, Observer<T> observer) {
    ObservableGroup group = findOrCreateGroup(groupId);
    return group.addAndExecute(tag, observable, observer);
  }

  /**
   * Checks if the given {@link Observable} has already been added to the group with
   * {@link #execute(int, String, Observable, Observer)}. If so, it updates the existing Observable
   * to use the provided observer.
   * Otherwise the request is executed as normal. This does not change locked status.
   */
  public <T> void executeOrResubscribe(
      int groupId, String tag, Observable<T> observable, Observer<T> observer) {
    if (hasObservable(groupId, tag)) {
      resubscribe(groupId, tag, observer);
    } else {
      execute(groupId, tag, observable, observer);
    }
  }

  /**
   * Returns {@code true} if an Observable exists under the given id or {@code false} otherwise,
   * either because it was never added, it has been completed, or it was removed manually.
   */
  public boolean hasObservable(int groupId, String tag) {
    return findOrCreateGroup(groupId).hasObservable(tag);
  }

  /** Unsubscribe observers from a given groupId. If the group doesn't exist, this does nothing */
  public void unsubscribe(int groupId) {
    ObservableGroup observableGroup = observableGroupMap.get(groupId);
    if (observableGroup != null) {
      observableGroup.unsubscribe();
    }
  }

  /**
   * Resubscribes an Observer to a Request with the provided groupId. If the request already has
   * a response, it will be immediately delivered by the Observable if it's unlocked.
   *
   * @throws IllegalStateException if no request with the given class and groupId was found.
   */
  public void resubscribe(int groupId, String tag, Observer<?> observer) {
    ObservableGroup observableGroup = observableGroupMap.get(groupId);
    Preconditions.checkState(observableGroup != null, "must execute request first");
    observableGroup.resubscribe(tag, observer);
  }

  /**
   * Clear all requests added with the given id. Queued results will be cleared, references will
   * be released, and no future results will be returned.
   * If the group doesn't exist, this does nothing.
   */
  public void cancel(int groupId) {
    findOrCreateGroup(groupId).cleanUp();
    observableGroupMap.remove(groupId);
  }

  /**
   * Locks (prevents) Observables added with the the given groupId from emitting events
   * until they are unlocked.
   */
  public void lock(int groupId) {
    findOrCreateGroup(groupId).lock();
  }

  /**
   * Unlock observers with the given groupId. Available events will be fired immediately if an
   * Observer has been added. If the group doesn't exist, this does nothing.
   */
  public void unlock(int groupId) {
    findOrCreateGroup(groupId).unlock();
  }

  private ObservableGroup findOrCreateGroup(int groupId) {
    ObservableGroup observableGroup = observableGroupMap.get(groupId);

    if (observableGroup == null) {
      observableGroup = new ObservableGroup();
      observableGroupMap.put(groupId, observableGroup);
    }

    return observableGroup;
  }
}
