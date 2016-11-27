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

import rx.Observable;
import rx.Observer;
import rx.Subscriber;
import rx.functions.Action0;
import rx.functions.Action1;

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
  private final Map<String, Map<String, ManagedObservable<?>>> groupMap = new ConcurrentHashMap<>();
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
   */
  <T> void add(final String observerTag, final String observableTag, Observable<T> observable, Observer<? super T> observer) {
    checkNotDestroyed();

    final Map<String, ManagedObservable<?>> existingObservables = getObservablesForObserver(observerTag);
    ManagedObservable<?> previousObservable = existingObservables.get(observableTag);

    if (previousObservable != null) {
      cancelAndRemove(observerTag, observableTag);
    }

    ManagedObservable<T> managedObservable =
        new ManagedObservable<>(observerTag, observableTag, observable, observer, new Action0() {
          @Override public void call() {
              existingObservables.remove(observableTag);
          }
        });

    existingObservables.put(observableTag, managedObservable);

    if (!locked) {
      managedObservable.unlock();
    }
  }

    private Map<String, ManagedObservable<?>> getObservablesForObserver(AutoResubscribingObserver<?> observer){
        return getObservablesForObserver(observer.tag);
    }

    private Map<String, ManagedObservable<?>> getObservablesForObserver(String observerTag){
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
  public <T> Observable.Transformer<? super T, T> transform(AutoResubscribingObserver<? super T> observer, String observableTag) {
    return new GroupSubscriptionTransformer<>(this, observer.tag, observableTag);
  }

    public <T> Observable.Transformer<? super T, T> transform(AutoResubscribingObserver<? super T> observer) {
        return new GroupSubscriptionTransformer<>(this, observer.tag, null);
    }

    public <T> Observable.Transformer<? super T, T> transform(String observerTag) {
        return new GroupSubscriptionTransformer<>(this, observerTag, null);
    }

    private void forAllObservables(Action1<ManagedObservable<?>> action){
        for (Map<String, ManagedObservable<?>> observableMap : groupMap.values()) {
            for (ManagedObservable<?> managedObservable : observableMap.values()) {
                action.call(managedObservable);
            }
        }
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

  /**
   * Locks (prevents) Observables added to this group from emitting new events. Observables added
   * via {@link #transform(AutoResubscribingObserver, String)} while the group is locked will **not** be subscribed until
   * their respective group is unlocked. If it's never unlocked, then the Observable will never be
   * subscribed to at all. This does not clear references to existing Observers. Please use
   * {@link #unsubscribe()} if you want to clear references to existing Observers.
   */
  public void lock() {
    locked = true;
      forAllObservables(new Action1<ManagedObservable<?>>() {
          @Override
          public void call(ManagedObservable<?> managedObservable) {
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
      forAllObservables(new Action1<ManagedObservable<?>>() {
          @Override
          public void call(ManagedObservable<?> managedObservable) {
              managedObservable.unlock();
          }
      });
  }

  /**
   * Unsubscribes from all Observables managed by this group. Also clears any references to existing
   * {@link Observer} objects in order to avoid leaks. This does not disconnect from the upstream
   * Observable, so it can be resumed upon calling {@link #observable(String)} if needed.
   */
  public void unsubscribe() {
      forAllObservables(new Action1<ManagedObservable<?>>() {
          @Override
          public void call(ManagedObservable<?> managedObservable) {
              managedObservable.unsubscribe();
          }
      });
  }

  /**
   * Returns an existing {@link Observable} for the provided {@code tag}. Does not change the
   * locked status of this {@link ObservableGroup}. If it is unlocked, and the Observable has
   * already emitted events, they will be immediately delivered. If it is locked then no events
   * will be delivered until it is unlocked.
   */
  public <T> Observable<T> observable(String observerTag) {
      return observable(observerTag, null);
  }

    public <T> Observable<T> observable(AutoResubscribingObserver<? super T> observer) {
        return observable(observer.tag, null);
    }

    public <T> Observable<T> observable(AutoResubscribingObserver<? super T> observer, String observableTag) {
        return observable(observer.tag, observableTag);
    }

    public <T> Observable<T> observable(String observerTag, String observableTag) {
        checkNotDestroyed();
        Map<String, ManagedObservable<?>> observables = getObservablesForObserver(observerTag);
        //noinspection unchecked
        ManagedObservable<T> managedObservable = (ManagedObservable<T>) observables.get(observableTag);
        Observable<T> observable = managedObservable.observable();
        return observable.compose(new GroupResubscriptionTransformer<>(this, managedObservable));
    }


  /**
   * @return a {@link RequestSubscription} with which the {@link Observer} can unsubscribe
   * from or cancel before the {@link Observable} has completed. If no {@link Observable} is found
   * for the provided {@code tag}, {@code null} is returned instead.
   */
  public RequestSubscription subscription(AutoResubscribingObserver<?> observer, String observableTag) {
      Map<String, ManagedObservable<?>> observables = getObservablesForObserver(observer);
      return observables.get(observableTag);
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
  public void cancelAndRemove(AutoResubscribingObserver<?> observer, String observableTag) {
      cancelAndRemove(observer.tag, observableTag);
  }

    public void cancelAndRemove(AutoResubscribingObserver<?> observer) {
        cancelAndRemove(observer.tag, null);
    }

    public void cancelAndRemove(String observerTag) {
        cancelAndRemove(observerTag, null);
    }

  /**
   * Removes the supplied {@link Observable} from this group and cancels it subscription. No more
   * events will be delivered to its subscriber.
   */
  public void cancelAndRemove(String observerTag, String observableTag) {
      Map<String, ManagedObservable<?>> observables = getObservablesForObserver(observerTag);
      ManagedObservable<?> managedObservable = observables.get(observableTag);
      if (managedObservable != null) {
          managedObservable.cancel();
          observables.remove(observableTag);
      }
  }

  /** Returns whether an {@link Observable} exists for the provided {@code tag} */
  public boolean hasObservable(AutoResubscribingObserver<?> observer, String observableTag) {
      return subscription(observer, observableTag) != null;
  }

  /** Returns whether this group has been already destroyed or not. */
  public boolean isDestroyed() {
    return destroyed;
  }

  private void checkNotDestroyed() {
    Preconditions.checkState(!destroyed, "Group is already destroyed! id=" + groupId);
  }

  @Override public String toString() {
    return "ObservableGroup{" + "groupMap=" + groupMap + ", groupId=" + groupId + ", locked="
        + locked + ", destroyed=" + destroyed + '}';
  }
}
