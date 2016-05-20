package com.airbnb.rxgroups;

import com.airbnb.rxgroups.LifecycleResubscription.ObserverInfo;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

import rx.Observer;
import rx.observers.TestSubscriber;

import static org.assertj.core.api.Assertions.assertThat;

public class LifecycleResubscriptionTest extends BaseTest {
  private final LifecycleResubscription resubscription = new LifecycleResubscription();
  private final TestSubscriber<ObserverInfo> testSubscriber = new TestSubscriber<>();

  @Test public void testObservers() {
    TestFragment testFragment = new TestFragment();
    resubscription.observers(testFragment).subscribe(testSubscriber);

    testSubscriber.awaitTerminalEvent(1, TimeUnit.SECONDS);
    testSubscriber.assertValueCount(2);
    testSubscriber.assertCompleted();
    testSubscriber.assertNoErrors();

    assertThat(testSubscriber.getOnNextEvents()).containsOnly(
        new ObserverInfo("Object.class", testFragment.observer1),
        new ObserverInfo("String.class", testFragment.observer2));
  }

  @Test public void testGetFieldsOnSuperClass() {
    SubTestFragment subTestFragment = new SubTestFragment();
    resubscription.observers(subTestFragment).subscribe(testSubscriber);

    testSubscriber.awaitTerminalEvent(1, TimeUnit.SECONDS);
    testSubscriber.assertCompleted();
    testSubscriber.assertNoErrors();

    assertThat(testSubscriber.getOnNextEvents()).containsOnly(
        new ObserverInfo("Object.class", subTestFragment.observer1),
        new ObserverInfo("String.class", subTestFragment.observer2),
        new ObserverInfo("Integer.class", subTestFragment.foo),
        new ObserverInfo("Long.class", subTestFragment.bar));
  }

  @Test
  public void testMultipleRequestsPerListener() {
    MultiRequestTestFragment fragment = new MultiRequestTestFragment();
    resubscription.observers(fragment).subscribe(testSubscriber);

    testSubscriber.awaitTerminalEvent(1, TimeUnit.SECONDS);
    testSubscriber.assertValueCount(2);
    testSubscriber.assertCompleted();
    testSubscriber.assertNoErrors();

    assertThat(testSubscriber.getOnNextEvents()).containsOnly(
        new ObserverInfo("Class.class", fragment.baz),
        new ObserverInfo("Double.class", fragment.baz));
  }

  static class TestFragment {
    @AutoResubscribe("Object.class") Observer<String> observer1 = new TestSubscriber<>();
    @AutoResubscribe("String.class") Observer<String> observer2 = new TestSubscriber<>();
  }

  private static class SubTestFragment extends TestFragment {
    @AutoResubscribe("Integer.class") Observer<String> foo = new TestSubscriber<>();
    @AutoResubscribe("Long.class") Observer<String> bar = new TestSubscriber<>();
  }

  private static class MultiRequestTestFragment {
    @AutoResubscribe({ "Class.class", "Double.class" }) Observer<String> baz =
        new TestSubscriber<>();
  }
}