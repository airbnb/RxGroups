package com.airbnb.rxgroups.android;

import android.app.Activity;
import android.app.Fragment;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import com.airbnb.rxgroups.ObservableGroup;
import com.airbnb.rxgroups.ObservableManager;
import com.airbnb.rxgroups.Preconditions;

import javax.annotation.Nullable;

import rx.Observable;
import rx.Observer;
import rx.Subscription;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.subscriptions.CompositeSubscription;

public class GroupLifecycleManager {
  private static final String TAG = "GroupLifecycleManager";
  private static final String KEY_STATE = "KEY_GROUPLIFECYCLEMANAGER_STATE";

  private final CompositeSubscription pendingSubscriptions = new CompositeSubscription();
  private final LifecycleResubscription resubscription;
  private final ObservableManager observableManager;
  private final ObservableTagFactory tagFactory;
  private final ObservableGroup group;
  private boolean hasSavedState;

  private GroupLifecycleManager(ObservableManager observableManager, ObservableGroup group,
      ObservableTagFactory tagFactory, LifecycleResubscription resubscription) {
    this.observableManager = observableManager;
    this.group = group;
    this.tagFactory = tagFactory;
    this.resubscription = resubscription;
  }

  public static GroupLifecycleManager onCreate(ObservableManager observableManager,
      ObservableTagFactory tagFactory, LifecycleResubscription resubscription) {
    return onCreate(observableManager, tagFactory, resubscription, null, null);
  }

  public static GroupLifecycleManager onCreate(ObservableManager observableManager,
      ObservableTagFactory tagFactory, @Nullable Bundle savedState, @Nullable Object target) {
    return onCreate(observableManager, tagFactory, new LifecycleResubscription(), savedState,
        target);
  }

  public static GroupLifecycleManager onCreate(ObservableManager observableManager,
      ObservableTagFactory tagFactory, LifecycleResubscription resubscription,
      @Nullable Bundle savedState, @Nullable Object target) {
    // Only need to resubscribe if restoring from saved state
    boolean shouldResubscribe = target != null && savedState != null;
    ObservableGroup group;

    if (savedState != null) {
      State state = savedState.getParcelable(KEY_STATE);

      Preconditions.checkState(state != null, "Must call onSaveInstanceState() first");

      // First check the instance hashCode before restoring state. If it's not the same instance,
      // then we have to create a new group since the previous one is already destroyed.
      // Android can sometimes reuse the same instance after saving state and we can't reliably
      // determine when that happens. This is a workaround for that behavior.
      if (state.managerHashCode != observableManager.hashCode()) {
        group = observableManager.newGroup();
      } else {
        group = observableManager.getGroup(state.groupId);
        if (group.isDestroyed()) {
          Log.w(TAG, "Tried to reuse GroupLifecycleManager with a destroyed group");
          group = observableManager.newGroup();
          shouldResubscribe = false;
        }
      }
    } else {
      group = observableManager.newGroup();
    }

    group.lock();

    GroupLifecycleManager manager = new GroupLifecycleManager(observableManager, group, tagFactory,
        resubscription);

    if (shouldResubscribe) {
      manager.subscribe(target);
    }

    return manager;
  }

  public ObservableGroup group() {
    return group;
  }


  /**
   * Returns whether the provided {@link Class} exists for the {@link ObservableGroup}.
   * Observables will only be removed from their respective groups once {@link
   * Observer#onCompleted()} has been called.
   */
  public boolean hasObservable(Class<?> klass) {
    return hasObservable(tagFactory.tag(klass));
  }

  public boolean hasObservable(String tag) {
    return group.hasObservable(tag);
  }

  /**
   * Subscribe all Observer fields on the target that are annotated with {@link AutoResubscribe}
   * and that have their corresponding Observable in flight.
   */
  public void subscribe(Object target) {
    Subscription subscription = resubscription.observers(target)
        .filter(new Func1<LifecycleResubscription.ObserverInfo, Boolean>() {
          @Override public Boolean call(LifecycleResubscription.ObserverInfo observerInfo) {
            return hasObservable(observerInfo.klass);
          }
        }).subscribe(new Action1<LifecycleResubscription.ObserverInfo>() {
          @Override public void call(LifecycleResubscription.ObserverInfo observerInfo) {
            resubscribe(observerInfo.klass, observerInfo.listener);
          }
        });

    pendingSubscriptions.add(subscription);
  }

  /**
   * Resubscribes an existing {@link Class} If the Observable has already emitted events,
   * they will be immediately delivered if the group is
   * unlocked. Any previously subscribed Observers will be unsubscribed before the new one.
   */
  public void resubscribe(Class<?> klass, Observer<?> observer) {
    resubscribe(tagFactory.tag(klass), observer);
  }

  @SuppressWarnings({ "unchecked", "rawtypes" })
  public void resubscribe(String tag, Observer<?> observer) {
    Observable observable = group.observable(tag);
    observable.subscribe(observer);
  }

  /**
   * Cancel the given request if it is running, so that no future request callbacks are received.
   * If the request is not running then nothing
   * happens.
   */
  public void cancelAndRemove(Class<?> klass) {
    group.cancelAndRemove(tagFactory.tag(klass));
  }

  private void onDestroy(boolean isFinishing) {
    pendingSubscriptions.clear();

    if (isFinishing) {
      observableManager.destroy(group);
    } else {
      group.unsubscribe();
    }
  }

  public void onDestroy(@Nullable Activity activity) {
    // We need to track whether the current Activity is finishing or not in order to decide if we
    // should destroy the ObservableGroup. If the Activity is not finishing, then we should not
    // destroy it, since we know that we're probably restoring state at some point and reattaching
    // to the existing ObservableGroup. If saveState() was not called, then it's likely that we're
    // being destroyed and are not ever coming back. However, this isn't perfect, especially
    // when using fragments in a ViewPager. We might want to allow users to explicitly destroy it
    // instead, in order to mitigate this issue.
    onDestroy(!hasSavedState || activity != null && activity.isFinishing());
  }

  public void onDestroy(Fragment fragment) {
    onDestroy(fragment.getActivity());
  }

  public void onResume() {
    hasSavedState = false;
    unlock();
  }

  public void onPause() {
    lock();
  }

  public void lock() {
    group.lock();
  }

  public void unlock() {
    group.unlock();
  }

  public void onSaveInstanceState(Bundle outState) {
    hasSavedState = true;
    outState.putParcelable(KEY_STATE, new State(observableManager.hashCode(), group.id()));
  }

  static class State implements Parcelable {
    final int managerHashCode;
    final long groupId;

    State(int managerHashCode, long groupId) {
      this.managerHashCode = managerHashCode;
      this.groupId = groupId;
    }

    @Override public int describeContents() {
      return 0;
    }

    @Override public void writeToParcel(Parcel dest, int flags) {
      dest.writeInt(managerHashCode);
      dest.writeLong(groupId);
    }

    public static final Parcelable.Creator<State> CREATOR = new Parcelable.Creator<State>() {
      @Override
      public State[] newArray(int size) {
        return new State[size];
      }

      @Override
      public State createFromParcel(Parcel source) {
        return new State(source.readInt(), source.readLong());
      }
    };
  }
}
