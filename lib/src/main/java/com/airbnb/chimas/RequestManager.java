package com.airbnb.chimas;

import android.util.SparseArray;

import com.google.common.base.Preconditions;

import rx.Observable;
import rx.Observer;
import rx.Subscription;

/**
 * Easily keep reference to AirRequests across lifecycle changes. Requests are grouped by a unique
 * id, which allows you to manage and reclaim requests made with the same id. Execute requests, and
 * then lock or unlock their listeners to control when you get the response back. Results will be
 * held in a queue until a observer is added and the id is unlocked.
 * <p/>
 * This is not threadsafe and should only be used from the main thread.
 */
public final class RequestManager {

  /** Map ids to a group of observables. */
  private final SparseArray<ObservableGroup> observableGroupMap = new SparseArray<>();

  /**
   * Fires the provided request and saves it under the given id. If a request of the same class is
   * already running, the previous one will be canceled and removed from this manager.
   */
  public <T> Subscription execute(
      int groupId, String tag, Observable<T> observable, Observer<T> observer) {
    ObservableGroup group = findOrCreateGroup(groupId);
    return group.addAndExecute(tag, observable, observer);
  }

  /**
   * Checks if the given request has already been added to the group with {@link #execute(int,
   * String, Observable, Observer)}. If so, it updates the existing request to use the given
   * request's observer.
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
   * True if a request exists under the given id. False if the request doesn't exist, either
   * because it was never added, it finished, or it was removed manually.
   */
  public boolean hasObservable(int groupId, String tag) {
    return findOrCreateGroup(groupId).hasObservable(tag);
  }

  /** Unsubscribe listeners from a given groupId. If the group doesn't exist, this does nothing */
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
  public void resubscribe(int groupId, String tag, Observer observer) {
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
   * Locks (prevents) request Observables added with the the given groupId from emitting events
   * until they are unlocked.
   */
  public void lock(int groupId) {
    findOrCreateGroup(groupId).lock();
  }

  /**
   * Unlock requests with the given groupId. Available events will be fired immediately if an
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