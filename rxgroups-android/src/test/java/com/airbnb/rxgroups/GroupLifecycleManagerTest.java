package com.airbnb.rxgroups;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;

import com.airbnb.rxgroups.android.BuildConfig;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Matchers;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.annotation.Config;

import rx.Observer;
import rx.schedulers.Schedulers;
import rx.schedulers.TestScheduler;
import rx.subjects.PublishSubject;
import rx.subjects.TestSubject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Config(sdk = Build.VERSION_CODES.LOLLIPOP, constants = BuildConfig.class)
@RunWith(RobolectricGradleTestRunner.class)
public class GroupLifecycleManagerTest extends BaseTest {
  private final TestScheduler scheduler = Schedulers.test();
  private final TestSubject<String> testSubject = TestSubject.create(scheduler);
  private final ObservableManager observableManager = mock(ObservableManager.class);
  private final ObservableGroup group = mock(ObservableGroup.class);
  private final TestTarget target = new TestTarget();

  static class TestTarget {
    @AutoResubscribe
    final TestAutoResubscribingObserver observer = new TestAutoResubscribingObserver("foo");

    @AutoResubscribe
    final TaggedObserver taggedObserver = new TaggedObserver() {
      @Override public String getTag() {
        return "bar";
      }

      @Override public void onCompleted() {

      }

      @Override public void onError(Throwable e) {

      }

      @Override public void onNext(Object o) {

      }
    };
  }

  @Test
  public void testSubscribe() {
    when(observableManager.newGroup()).thenReturn(group);
    doCallRealMethod().when(group).initializeAutoTaggingAndResubscription(Matchers.any());
    GroupLifecycleManager.onCreate(observableManager, null, target);
    verify(group).resubscribeAll(target.observer);
    verify(group).resubscribeAll(target.taggedObserver);
  }

  @Test
  public void testSubscribeNoObservables() {
    when(observableManager.newGroup()).thenReturn(group);
    GroupLifecycleManager.onCreate(observableManager, null, null);
    verify(group, never()).resubscribeAll(any(AutoResubscribingObserver.class));
  }

  @Test
  public void testCreateWithNoNullTarget() {
    when(observableManager.newGroup()).thenReturn(group);
    GroupLifecycleManager.onCreate(observableManager, null, null);
    verify(group, never()).resubscribeAll(any(AutoResubscribingObserver.class));
    verify(group, never()).resubscribeAll(any(TaggedObserver.class));
  }

  public void testSubscribeInvalidTargetNoException() {
    when(observableManager.newGroup()).thenReturn(group);
    GroupLifecycleManager.onCreate(observableManager, null, new Object());
  }

  @Test(expected = NullPointerException.class)
  public void testSubscribeNullTargetFails() {
    when(observableManager.newGroup()).thenReturn(group);
    GroupLifecycleManager groupLifecycleManager = GroupLifecycleManager.onCreate
            (observableManager, null, null);

    groupLifecycleManager.initializeAutoTaggingAndResubscription(null);
  }

  @Test
  public void testDestroyFinishingActivity() {
    when(observableManager.newGroup()).thenReturn(group);
    when(group.hasObservables(target.observer)).thenReturn(true);
    when(group.observable(target.observer)).thenReturn(testSubject);
    when(group.hasObservables(target.taggedObserver)).thenReturn(true);
    when(group.observable(target.taggedObserver)).thenReturn(testSubject);

    GroupLifecycleManager lifecycleManager = GroupLifecycleManager.onCreate
            (observableManager, null, target);

    Activity activity = mock(Activity.class);
    when(activity.isFinishing()).thenReturn(true);
    lifecycleManager.onDestroy(activity);

    verify(observableManager).destroy(group);
  }

  @Test public void testNonResubscribableObservablesRemovedAfterNonFinishingDestroy() {
    when(observableManager.newGroup()).thenReturn(new ObservableGroup(1));

    GroupLifecycleManager lifecycleManager = GroupLifecycleManager.onCreate
        (observableManager, null, target);

    Observer nonResubscribableObserver = new Observer<Object>() {
      @Override public void onCompleted() {

      }

      @Override public void onError(Throwable e) {

      }

      @Override public void onNext(Object o) {

      }
    };
    lifecycleManager.group().add(Utils.getObserverTag(nonResubscribableObserver),
        "observableTag", PublishSubject.create(), nonResubscribableObserver);

    assertThat(lifecycleManager.group().hasObservables(nonResubscribableObserver)).isTrue();

    //Simulate a rotation
    Activity activity = mock(Activity.class);
    when(activity.isFinishing()).thenReturn(false);
    lifecycleManager.onSaveInstanceState(new Bundle());
    lifecycleManager.onDestroy(activity);

    assertThat(lifecycleManager.group().hasObservables(nonResubscribableObserver)).isFalse();
  }

  @Test public void testResubscribableNotRemovedAfterNonFinishingDestroy() {
    when(observableManager.newGroup()).thenReturn(new ObservableGroup(1));

    GroupLifecycleManager lifecycleManager = GroupLifecycleManager.onCreate
        (observableManager, null, target);

    Observer stableObserver = new TaggedObserver() {
      @Override public String getTag() {
        return "stableTag";
      }

      @Override public void onCompleted() {

      }

      @Override public void onError(Throwable e) {

      }

      @Override public void onNext(Object o) {

      }
    };
    lifecycleManager.group().add(Utils.getObserverTag(stableObserver),
        "observableTag", PublishSubject.create(), stableObserver);

    assertThat(lifecycleManager.group().hasObservables(stableObserver)).isTrue();

    //Simulate a rotation
    Activity activity = mock(Activity.class);
    when(activity.isFinishing()).thenReturn(false);
    lifecycleManager.onSaveInstanceState(new Bundle());
    lifecycleManager.onDestroy(activity);

    assertThat(lifecycleManager.group().hasObservables(stableObserver)).isTrue();
  }

}