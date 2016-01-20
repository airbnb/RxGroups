/*
 * Copyright (C) 2016 Airbnb, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.airbnb.rxgroups;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import rx.Observable;
import rx.Observer;
import rx.Subscriber;

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
public class ObservableGroup {
  private final Map<String, ManagedObservable<?>> groupMap = new ConcurrentHashMap<>();
  private final long groupId;
  private boolean locked;
  private boolean destroyed;

  ObservableGroup(long groupId) {
    this.groupId = groupId;
  }

  public long id() {
    return groupId;
  }

  /**
   * Adds an {@link Observable} and {@link Observer} to this group and subscribes to it. If an
   * {@link Observable} with the same tag is already added, the previous one will be canceled and
   * removed before adding and subscribing to the new one.
   *
   * @return a {@link RequestSubscription} reference with which the {@link Observer} can stop
   * receiving items before the {@link Observable} has completed.
   * @throws IllegalStateException if an {@link Observable} with the same tag is already added and
   *                               not yet completed.
   */
  <T> RequestSubscription add(String tag, Observable<T> observable, Observer<? super T> observer) {
    checkNotDestroyed();

    ManagedObservable<?> previousObservable = groupMap.get(tag);

    if (previousObservable != null) {
      cancelAndRemove(previousObservable);
    }

    ManagedObservable<T> managedObservable =
        new ManagedObservable<>(tag, observable, observer, () -> groupMap.remove(tag));

    groupMap.put(tag, managedObservable);

    if (!locked) {
      managedObservable.unlock();
    }

    return managedObservable;
  }

  /**
   * Transforms an existing {@link Observable} by returning a new {@link Observable} that is
   * automatically added to this {@link ObservableGroup} with the provided {@code tag} when
   * subscribed to.
   */
  public <T> Observable.Transformer<? super T, T> transform(String tag) {
    return new GroupSubscriptionTransformer<>(this, tag);
  }

  /**
   * Cancels all subscriptions and releases references to Observables and Observers. No more
   * Observables can be added to this group after it has been destroyed and it becomes unusable.
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
   * via {@link #transform(String tag)} while the group is locked will **not** be subscribed until
   * their respective group is unlocked. If it's never unlocked, then the Observable will never be
   * subscribed to at all. This does not clear references to existing Observers. Please use
   * {@link #unsubscribe()} if you want to clear references to existing Observers.
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
   * Unsubscribes from all Observables managed by this group. Also clears any references to existing
   * {@link Observer} objects in order to avoid leaks. This does not disconnect from the upstream
   * Observable, so it can be resumed upon calling {@link #observable(String)} if needed.
   */
  public void unsubscribe() {
    for (ManagedObservable<?> managedObservable : groupMap.values()) {
      managedObservable.unsubscribe();
    }
  }

  /**
   * Returns an existing {@link Observable} for the provided {@code tag}. Does not change the
   * locked status of this {@link ObservableGroup}. If it is unlocked, and the Observable has
   * already emitted events, they will be immediately delivered. If it is locked then no events
   * will be delivered until it is unlocked.
   */
  @SuppressWarnings({ "rawtypes", "unchecked" }) public <T> Observable<T> observable(String tag) {
    checkNotDestroyed();
    ManagedObservable<T> managedObservable = (ManagedObservable<T>) groupMap.get(tag);
    Observable<T> observable = managedObservable.observable();
    return observable.compose(new GroupResubscriptionTransformer<>(this, managedObservable));
  }

  <T> void resubscribe(ManagedObservable<T> managedObservable, Observable<T> observable,
      Subscriber<? super T> subscriber) {
    managedObservable.resubscribe(observable, subscriber);
  }

  /**
   * Removes the supplied {@link Observable} from this group and cancels it subscription. No more
   * events will be delivered to its subscriber. If no Observable is found for the provided tag,
   * nothing happens.
   */
  public void cancelAndRemove(String tag) {
    cancelAndRemove(groupMap.get(tag));
  }

  /**
   * Removes the supplied {@link Observable} from this group and cancels it subscription. No more
   * events will be delivered to its subscriber.
   */
  private void cancelAndRemove(ManagedObservable<?> managedObservable) {
    if (managedObservable != null) {
      managedObservable.cancel();
      groupMap.remove(managedObservable.tag());
    }
  }

  /** Returns whether an {@link Observable} exists for the provided {@code tag} */
  public boolean hasObservable(String tag) {
    ManagedObservable<?> managedObservable = groupMap.get(tag);
    return managedObservable != null;
  }

  private void checkNotDestroyed() {
    Preconditions.checkState(!destroyed, "Group is already destroyed! id=" + groupId);
  }

  @Override public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ObservableGroup that = (ObservableGroup) o;

    if (groupId != that.groupId) return false;
    if (locked != that.locked) return false;
    //noinspection SimplifiableIfStatement
    if (destroyed != that.destroyed) return false;
    return groupMap.equals(that.groupMap);

  }

  @Override public int hashCode() {
    int result = groupMap.hashCode();
    result = 31 * result + (int) (groupId ^ (groupId >>> 32));
    result = 31 * result + (locked ? 1 : 0);
    result = 31 * result + (destroyed ? 1 : 0);
    return result;
  }

  @Override public String toString() {
    return "ObservableGroup{" + "groupMap=" + groupMap + ", groupId=" + groupId + ", locked="
        + locked + ", destroyed=" + destroyed + '}';
  }

  boolean isDestroyed() {
    return destroyed;
  }
}
