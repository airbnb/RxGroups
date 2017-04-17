package com.airbnb.rxgroups;

import android.app.Activity;
import android.os.Build;

import com.airbnb.rxgroups.android.BuildConfig;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.annotation.Config;

import rx.schedulers.Schedulers;
import rx.schedulers.TestScheduler;
import rx.subjects.TestSubject;

import static org.mockito.Matchers.any;
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
  }

  @Test
  public void testSubscribe() {
    when(observableManager.newGroup()).thenReturn(group);
    GroupLifecycleManager.onCreate(observableManager, null, target);
    verify(group).resubscribe(target.observer);
  }

  @Test
  public void testSubscribeNoObservables() {
    when(observableManager.newGroup()).thenReturn(group);
    GroupLifecycleManager.onCreate(observableManager, null, null);
    verify(group, never()).resubscribe(any(AutoResubscribingObserver.class));
  }

  @Test
  public void testCreateWithNoNullTarget() {
    when(observableManager.newGroup()).thenReturn(group);
    GroupLifecycleManager.onCreate(observableManager, null, null);
    verify(group, never()).resubscribe(any(AutoResubscribingObserver.class));
  }

  @Test(expected = IllegalArgumentException.class)
  public void testSubscribeInvalidTarget() {
    when(observableManager.newGroup()).thenReturn(group);
    GroupLifecycleManager.onCreate(observableManager, null, new Object());
  }

  @Test(expected = IllegalArgumentException.class)
  public void testSubscribeNullTargetFails() {
    when(observableManager.newGroup()).thenReturn(group);
    GroupLifecycleManager groupLifecycleManager = GroupLifecycleManager.onCreate
            (observableManager, null, null);

    groupLifecycleManager.initializeAutoResubscription(null);
  }

  @Test
  public void testDestroyFinishingActivity() {
    when(observableManager.newGroup()).thenReturn(group);
    when(group.hasObservables(target.observer)).thenReturn(true);
    when(group.observable(target.observer)).thenReturn(testSubject);

    GroupLifecycleManager lifecycleManager = GroupLifecycleManager.onCreate
            (observableManager, null, target);

    Activity activity = mock(Activity.class);
    when(activity.isFinishing()).thenReturn(true);
    lifecycleManager.onDestroy(activity);

    verify(observableManager).destroy(group);
  }
}