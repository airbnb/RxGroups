package com.airbnb.rxgroups;

import android.app.Activity;
import android.os.Build;

import com.airbnb.rxgroups.android.BuildConfig;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.annotation.Config;

import java.util.concurrent.TimeUnit;

import rx.observers.TestSubscriber;
import rx.schedulers.Schedulers;
import rx.schedulers.TestScheduler;
import rx.subjects.TestSubject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Config(sdk = Build.VERSION_CODES.LOLLIPOP, constants = BuildConfig.class)
@RunWith(RobolectricGradleTestRunner.class)
public class GroupLifecycleManagerTest extends BaseTest {
  private final TestScheduler scheduler = Schedulers.test();
  private final TestSubject<String> testSubject = TestSubject.create(scheduler);
  private final TestSubscriber<String> testSubscriber = new TestSubscriber<>();
  private final ObservableManager observableManager = mock(ObservableManager.class);
  private final ObservableGroup group = mock(ObservableGroup.class);
  private final Object target = new Object();
  private final AutoResubscribingObserver<String> observer = new AutoResubscribingObserver<String>() {};

  @Test public void testSubscribe() {
    when(observableManager.newGroup()).thenReturn(group);
    when(group.hasObservables(observer)).thenReturn(true);
    when(group.observable(observer)).thenReturn(testSubject);

    GroupLifecycleManager.onCreate(observableManager, null, target);

    assertThat(testSubject.hasObservers()).isTrue();

    testSubject.onNext("hello");
    testSubject.onCompleted();
    scheduler.triggerActions();

    testSubscriber.awaitTerminalEvent(3, TimeUnit.SECONDS);
    testSubscriber.assertCompleted();
    testSubscriber.assertValue("hello");
  }

  @Test public void testSubscribeNoObservables() {
    when(observableManager.newGroup()).thenReturn(group);
    when(group.hasObservables(observer)).thenReturn(false);
    when(group.observable(observer)).thenReturn(testSubject);

    GroupLifecycleManager.onCreate(observableManager, null, target);

    assertThat(testSubject.hasObservers()).isFalse();

    testSubject.onNext("hello");
    testSubject.onCompleted();
    scheduler.triggerActions();

    testSubscriber.awaitTerminalEvent(3, TimeUnit.SECONDS);
    testSubscriber.assertNotCompleted();
    testSubscriber.assertNoValues();
  }

  @Test public void testSubscribeNoObservers() {
    when(observableManager.newGroup()).thenReturn(group);
    when(group.hasObservables(observer)).thenReturn(true);
    when(group.observable(observer)).thenReturn(testSubject);

    GroupLifecycleManager.onCreate(observableManager, null, target);

    assertThat(testSubject.hasObservers()).isFalse();

    testSubject.onNext("hello");
    testSubject.onCompleted();
    scheduler.triggerActions();
  }

  @Test public void testDestroyFinishingActivity() {
    when(observableManager.newGroup()).thenReturn(group);
    when(group.hasObservables(observer)).thenReturn(true);
    when(group.observable(observer)).thenReturn(testSubject);

    GroupLifecycleManager.onCreate(observableManager, null, target);

    Activity activity = mock(Activity.class);
    when(activity.isFinishing()).thenReturn(true);

    verify(observableManager).destroy(group);
  }
}