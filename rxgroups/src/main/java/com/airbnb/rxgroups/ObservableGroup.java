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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableTransformer;
import io.reactivex.Observer;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;

/**
 * A helper class for {@link ObservableManager} that groups {@link Observable}s to be managed
 * together. For example, a fragment will probably want to group all of its requests together so it
 * can lock/unlock/clear them all at once according to its lifecycle. <p> Requests are added to a
 * {@link ObservableGroup} with an observer, and that observer will be called when a response is
 * ready. If the {@link ObservableGroup} is locked when the response arrives, or if the observer was
 * removed, the response will be queued and delivered when the {@link ObservableGroup} is unlocked
 * and a observer is added. <p> Each {@link Observer} can only be subscribed to
 * the same proxiedObservable tag once. If a {@link Observer} is already subscribed to
 * the given tag, the original subscription will be cancelled and discarded.
 */
@SuppressWarnings("WeakerAccess")
public class ObservableGroup {
  private final Map<String, Map<String, ManagedObservable<?>>> groupMap = new
      ConcurrentHashMap<>();
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
   * Sets a unique tag on all Observer fields of {@code target}
   * that are annotated with {@link AutoTag} or {@link AutoResubscribe}.
   * All fields declared in superclasses of {@code target} will also be set.
   * <p>
   * Subscribe all Observer fields on the {@code target} and in superclasses
   * that are annotated with {@link AutoResubscribe}
   * and that have their corresponding Observable in flight.
   * <p>
   * Resubscribes all Observer fields on the target that are annotated with
   * {@link AutoResubscribe} and that have their corresponding Observable in flight.
   */
  public void initializeAutoTaggingAndResubscription(final Object target) {
    Preconditions.checkNotNull(target, "Target cannot be null");
    ResubscribeHelper.initializeAutoTaggingAndResubscription(target, this);
  }

  /**
   * Unlike {@link #initializeAutoTaggingAndResubscription(Object)} superclasses will
   * <b>not</b> be initialized; only the fields in {@code targetClass} will be initialized.
   * <p>
   * Sets a unique tag on all Observer fields of {@code target}
   * that are annotated with {@link AutoTag} or {@link AutoResubscribe}.
   * <p>
   * Subscribe all Observer fields on the {@code target}
   * that are annotated with {@link AutoResubscribe}
   * and that have their corresponding Observable in flight.
   * <p>
   * Resubscribes all Observer fields on the target that are annotated with
   * {@link AutoResubscribe} and that have their corresponding Observable in flight.
   * <p>
   */
  public <T> void initializeAutoTaggingAndResubscriptionInTargetClassOnly(T target,
      Class<T> targetClass) {
    Preconditions.checkNotNull(target, "Target cannot be null");
    ResubscribeHelper.initializeAutoTaggingAndResubscriptionInTargetClassOnly(target,
        targetClass, this);
  }

  /**
   * Adds an {@link Observable} and {@link Observer} to this group and subscribes to it. If an
   * {@link Observable} with the same tag is already added, the previous one will be canceled and
   * removed before adding and subscribing to the new one.
   */
  <T> ManagedObservable<T> add(final String observerTag, final String observableTag,
      Observable<T> observable, ObservableEmitter<? super T> observer) {
    checkNotDestroyed();
    final Map<String, ManagedObservable<?>> existingObservables =
        getObservablesForObserver(observerTag);
    ManagedObservable<?> previousObservable = existingObservables.get(observableTag);

    if (previousObservable != null) {
      cancelAndRemove(observerTag, observableTag);
    }

    ManagedObservable<T> managedObservable =
        new ManagedObservable<>(observerTag, observableTag, observable, observer, new
            Action() {
              @Override
              public void run() {
                existingObservables.remove(observableTag);
              }
            });

    existingObservables.put(observableTag, managedObservable);

    if (!locked) {
      managedObservable.unlock();
    }
    return managedObservable;
  }

  Map<String, ManagedObservable<?>> getObservablesForObserver(
      Observer<?> observer) {
    return getObservablesForObserver(Utils.getObserverTag(observer));
  }

  Map<String, ManagedObservable<?>> getObservablesForObserver(String observerTag) {
    Map<String, ManagedObservable<?>> map = groupMap.get(observerTag);
    if (map == null) {
      map = new HashMap<>();
      groupMap.put(observerTag, map);
    }
    return map;
  }

  /**
   * Transforms an existing {@link Observable} by returning a new {@link Observable} that is
   * automatically added to this {@link ObservableGroup} with the provided {@code tag} when
   * subscribed to.
   */
  public <T> ObservableTransformer<? super T, T> transform(Observer<? super T> observer,
      String observableTag) {
    return new GroupSubscriptionTransformer<>(this, Utils.getObserverTag(observer),
        observableTag);
  }

  /**
   * Transforms an existing {@link Observable} by returning a new {@link Observable} that is
   * automatically added to this {@link ObservableGroup}.
   * <p> Convenience method
   * for {@link #transform(Observer, String)} when {@code observer} only
   * is subscribed to one {@link Observable}.
   */
  public <T> ObservableTransformer<? super T, T> transform(Observer<? super T> observer) {
    return transform(observer, Utils.getObserverTag(observer));
  }

  /**
   * Cancels all subscriptions and releases references to Observables and Observers. No more
   * Observables can be added to this group after it has been destroyed and it becomes unusable.
   */
  void destroy() {
    destroyed = true;

    for (Map<String, ManagedObservable<?>> observableMap : groupMap.values()) {
      for (ManagedObservable<?> managedObservable : observableMap.values()) {
        managedObservable.cancel();
      }
      observableMap.clear();
    }
    groupMap.clear();
  }

  private void forAllObservables(Consumer<ManagedObservable<?>> action) {
    for (Map<String, ManagedObservable<?>> observableMap : groupMap.values()) {
      for (ManagedObservable<?> managedObservable : observableMap.values()) {
        try {
          action.accept(managedObservable);
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    }
  }

  /**
   * Locks (prevents) Observables added to this group from emitting new events. Observables added
   * via {@link #transform(Observer, String)} while the group is locked will
   * **not** be subscribed until their respective group is unlocked. If it's never unlocked, then
   * the Observable will never be subscribed to at all. This does not clear references to existing
   * Observers. Please use {@link #dispose()} if you want to clear references to existing
   * Observers.
   */
  public void lock() {
    locked = true;
    forAllObservables(new Consumer<ManagedObservable<?>>() {
      @Override
      public void accept(ManagedObservable<?> managedObservable) {
        managedObservable.lock();
      }
    });
  }

  /**
   * Unlocks (releases) Observables added to this group to emit new events until they are locked,
   * unsubscribed or cancelled.
   */
  public void unlock() {
    locked = false;
    forAllObservables(new Consumer<ManagedObservable<?>>() {
      @Override
      public void accept(ManagedObservable<?> managedObservable) {
        managedObservable.unlock();
      }
    });
  }

  /**
   * Disposes all Observables managed by this group. Also clears any references to existing
   * {@link Observer} objects in order to avoid leaks. This does not disconnect from the upstream
   * Observable, so it can be resumed upon calling {@link #observable(Observer)} if
   * needed.
   */
  public void dispose() {
    forAllObservables(new Consumer<ManagedObservable<?>>() {
      @Override
      public void accept(ManagedObservable<?> managedObservable) {
        managedObservable.dispose();
      }
    });
  }

  /**
   * Returns an existing {@link Observable} for the {@link TaggedObserver}.
   * <p>
   * <p> Does not change the locked status of this {@link ObservableGroup}.
   * If it is unlocked, and the Observable has already emitted events,
   * they will be immediately delivered. If it is locked then no events will be
   * delivered until it is unlocked.
   */
  public <T> Observable<T> observable(Observer<? super T> observer) {
    return observable(observer, Utils.getObserverTag(observer));
  }

  public <T> Observable<T> observable(Observer<? super T> observer, String observableTag) {
    checkNotDestroyed();
    String observerTag = Utils.getObserverTag(observer);
    Map<String, ManagedObservable<?>> observables = getObservablesForObserver(observerTag);
    //noinspection unchecked
    ManagedObservable<T> managedObservable = (ManagedObservable<T>) observables.get(
        observableTag);
    if (managedObservable == null) {
      throw new IllegalStateException("No observable exists for observer: "
          + observerTag + " and observable: " + observableTag);
    }

    Observable<T> observable = managedObservable.proxiedObservable();
    return observable.compose(new GroupResubscriptionTransformer<>(managedObservable));
  }

  /**
   * @return a {@link SourceSubscription} with which the {@link Observer} can dispose from or
   * cancel before the {@link Observable} has completed. If no {@link Observable} is found for the
   * provided {@code observableTag}, {@code null} is returned instead.
   */
  public SourceSubscription subscription(Observer<?> observer, String observableTag) {
    return subscription(Utils.getObserverTag(observer), observableTag);
  }

  /**
   * Convenience method for {@link #subscription(Observer, String)}, with
   * {@code observableTag} of {@link TaggedObserver#getTag()}.
   * <p>
   * <p> Use when the {@code observer} is associated with only one {@link Observable}.
   */
  public SourceSubscription subscription(Observer<?> observer) {
    return subscription(observer, Utils.getObserverTag(observer));
  }

  private SourceSubscription subscription(String observerTag, String observableTag) {
    Map<String, ManagedObservable<?>> observables = getObservablesForObserver(observerTag);
    return observables.get(observableTag);
  }

  public <T> void resubscribeAll(TaggedObserver<? super T> observer) {
    Map<String, ManagedObservable<?>> observables = getObservablesForObserver(observer);
    for (String observableTag : observables.keySet()) {
      observable(observer, observableTag).subscribe(observer);
    }
  }

  /**
   * Resubscribes the {@link TaggedObserver} to the observable identified by {@code observableTag}.
   */
  public <T> void resubscribe(TaggedObserver<? super T> observer, String observableTag) {
    final Observable<T> observable = observable(observer, observableTag);
    if (observable != null) {
      observable.subscribe(observer);
    }
  }

  /**
   * Resubscribes the {@link TaggedObserver} to the observable
   * identified by {@code observableTag}.
   * <p> Convenience method
   * for {@link #resubscribe(TaggedObserver, String)} when {@code observer} only
   * is subscribed to one {@link Observable}.
   */
  public <T> void resubscribe(TaggedObserver<? super T> observer) {
    resubscribe(observer, Utils.getObserverTag(observer));
  }

  /**
   * Removes the {@link Observable} identified by {@code observableTag} for the given
   * {@link Observer} and cancels it subscription.
   * No more events will be delivered to its subscriber.
   * <p>If no Observable is found for the provided {@code observableTag}, nothing happens.
   */
  public void cancelAndRemove(Observer<?> observer, String observableTag) {
    cancelAndRemove(Utils.getObserverTag(observer), observableTag);
  }

  /**
   * Removes all {@link Observable} for the given
   * {@link Observer} and cancels their subscriptions.
   * No more events will be delivered to its subscriber.
   */
  public void cancelAllObservablesForObserver(Observer<?> observer) {
    cancelAllObservablesForObserver(Utils.getObserverTag(observer));
  }

  private void cancelAllObservablesForObserver(String observerTag) {
    Map<String, ManagedObservable<?>> observables = getObservablesForObserver(observerTag);
    for (ManagedObservable<?> managedObservable : observables.values()) {
      managedObservable.cancel();
    }
    observables.clear();
  }

  /**
   * Removes the supplied {@link Observable} from this group and cancels it subscription. No more
   * events will be delivered to its subscriber.
   */
  private void cancelAndRemove(String observerTag, String observableTag) {
    Map<String, ManagedObservable<?>> observables = getObservablesForObserver(observerTag);
    ManagedObservable<?> managedObservable = observables.get(observableTag);
    if (managedObservable != null) {
      managedObservable.cancel();
      observables.remove(observableTag);
    }
  }

  /**
   * Returns whether the observer has an existing {@link Observable} with
   * the provided {@code observableTag}.
   */
  public boolean hasObservable(Observer<?> observer, String observableTag) {
    return subscription(observer, observableTag) != null;
  }

  /**
   * Returns whether the observer has any existing {@link Observable}.
   */
  public boolean hasObservables(Observer<?> observer) {
    return !getObservablesForObserver(observer).isEmpty();
  }

  /**
   * Returns whether this group has been already destroyed or not.
   */
  public boolean isDestroyed() {
    return destroyed;
  }

  private void checkNotDestroyed() {
    Preconditions.checkState(!destroyed, "Group is already destroyed! id=" + groupId);
  }

  @Override
  public String toString() {
    return "ObservableGroup{" + "groupMap=" + groupMap + ", groupId=" + groupId + ", locked="
        + locked + ", destroyed=" + destroyed + '}';
  }

  void removeNonResubscribableObservers() {
    for (String observerTag : groupMap.keySet()) {
      if (NonResubscribableTag.isNonResubscribableTag(observerTag)) {
        cancelAllObservablesForObserver(observerTag);
      }
    }
  }
}
