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

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.Callable;

import io.reactivex.Observable;
import io.reactivex.Scheduler;
import io.reactivex.annotations.NonNull;
import io.reactivex.functions.Function;
import io.reactivex.observers.TestObserver;
import io.reactivex.plugins.RxJavaPlugins;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;

public class ObservableGroupTest {
  private final ObservableManager observableManager = new ObservableManager();
  private final TestAutoResubscribingObserver fooObserver =
      new TestAutoResubscribingObserver("foo");
  private final TestAutoResubscribingObserver barObserver =
      new TestAutoResubscribingObserver("bar");

  @Before public void setUp() throws IOException {
    RxJavaPlugins.setInitIoSchedulerHandler(new Function<Callable<Scheduler>, Scheduler>() {
      @Override
      public Scheduler apply(@NonNull Callable<Scheduler> schedulerCallable) throws Exception {
        return Schedulers.trampoline();
      }
    });
  }

  @Test public void shouldNotHaveObservable() {
    ObservableGroup group = observableManager.newGroup();
    assertThat(group.hasObservables(fooObserver)).isEqualTo(false);
  }

  @Test public void shouldAddRequestByObserverTag() {
    ObservableGroup group = observableManager.newGroup();
    ObservableGroup group2 = observableManager.newGroup();
    Observable<String> sourceObservable = Observable.never();
    sourceObservable.compose(group.transform(fooObserver)).subscribe(fooObserver);

    assertThat(group.hasObservables(fooObserver)).isEqualTo(true);
    assertThat(group2.hasObservables(fooObserver)).isEqualTo(false);
    assertThat(group.hasObservables(barObserver)).isEqualTo(false);
  }

  @Test public void shouldNotBeCompleted() {
    ObservableGroup group = observableManager.newGroup();
    TestObserver<Object> subscriber = new TestObserver<>();
    Observable<String> sourceObservable = Observable.never();

    sourceObservable.compose(group.transform(fooObserver)).subscribe(fooObserver);
    subscriber.assertNotComplete();
  }

  @Test public void shouldBeSubscribed() {
    ObservableGroup group = observableManager.newGroup();
    Observable<String> sourceObservable = Observable.never();
    sourceObservable.compose(group.transform(fooObserver)).subscribe(fooObserver);

    assertThat(group.subscription(fooObserver).isCancelled()).isEqualTo(false);
  }

  @Test public void shouldDeliverSuccessfulEvent() throws Exception {
    ObservableGroup group = observableManager.newGroup();
    PublishSubject<String> sourceObservable = PublishSubject.create();
    TestObserver<String> subscriber = new TestObserver<>();

    sourceObservable.compose(group.transform(subscriber)).subscribe(subscriber);
    subscriber.assertNotComplete();

    sourceObservable.onNext("Foo Bar");
    sourceObservable.onComplete();

    subscriber.assertComplete();
    subscriber.assertValue("Foo Bar");
  }

  @Test public void shouldDeliverError() throws Exception {
    ObservableGroup group = observableManager.newGroup();
    TestObserver<String> testObserver = new TestObserver<>();
    Observable<String> sourceObservable = Observable.error(new RuntimeException("boom"));
    sourceObservable.compose(group.transform(testObserver)).subscribe(testObserver);

    testObserver.assertError(RuntimeException.class);
  }

  @Test public void shouldSeparateObservablesByGroupId() {
    ObservableGroup group = observableManager.newGroup();
    ObservableGroup group2 = observableManager.newGroup();
    Observable<String> observable1 = Observable.never();
    Observable<String> observable2 = Observable.never();

    observable1.compose(group.transform(barObserver)).subscribe(barObserver);
    assertThat(group.hasObservables(barObserver)).isEqualTo(true);
    assertThat(group.hasObservables(fooObserver)).isEqualTo(false);
    assertThat(group2.hasObservables(barObserver)).isEqualTo(false);
    assertThat(group2.hasObservables(fooObserver)).isEqualTo(false);

    observable2.compose(group2.transform(fooObserver)).subscribe(fooObserver);
    assertThat(group.hasObservables(barObserver)).isEqualTo(true);
    assertThat(group.hasObservables(fooObserver)).isEqualTo(false);
    assertThat(group2.hasObservables(barObserver)).isEqualTo(false);
    assertThat(group2.hasObservables(fooObserver)).isEqualTo(true);
  }

  @Test public void shouldClearObservablesByGroupId() {
    ObservableGroup group = observableManager.newGroup();
    ObservableGroup group2 = observableManager.newGroup();
    Observable<String> observable1 = Observable.never();
    Observable<String> observable2 = Observable.never();

    observable1.compose(group.transform(fooObserver)).subscribe(fooObserver);
    observable2.compose(group2.transform(fooObserver)).subscribe(fooObserver);

    observableManager.destroy(group);

    assertThat(group.hasObservables(fooObserver)).isEqualTo(false);
    assertThat(group2.hasObservables(fooObserver)).isEqualTo(true);
    assertThat(group.subscription(fooObserver)).isNull();
    assertThat(group2.subscription(fooObserver).isCancelled()).isEqualTo(false);

    observableManager.destroy(group2);
    assertThat(group.hasObservables(fooObserver)).isEqualTo(false);
    assertThat(group2.hasObservables(fooObserver)).isEqualTo(false);
    assertThat(group.subscription(fooObserver)).isNull();
    assertThat(group2.subscription(fooObserver)).isNull();
  }

  @Test public void shouldClearObservablesWhenLocked() {
    ObservableGroup group = observableManager.newGroup();
    Observable<String> observable1 = Observable.never();
    Observable<String> observable2 = Observable.never();
    TestObserver<String> subscriber1 = new TestObserver<>();
    TestObserver<String> subscriber2 = new TestObserver<>();

    observable1.compose(group.transform(subscriber1)).subscribe(subscriber1);
    observable2.compose(group.transform(subscriber2)).subscribe(subscriber2);

    group.dispose();
    observableManager.destroy(group);

    assertThat(group.hasObservables(fooObserver)).isEqualTo(false);
    assertThat(group.hasObservables(barObserver)).isEqualTo(false);
  }

  @Test public void shouldClearQueuedResults() throws Exception {
    ObservableGroup group = observableManager.newGroup();
    PublishSubject<String> sourceObservable = PublishSubject.create();
    TestObserver<String> subscriber1 = new TestObserver<>();

    sourceObservable.compose(group.transform(subscriber1)).subscribe(subscriber1);
    group.dispose();
    sourceObservable.onNext("Hello");
    sourceObservable.onComplete();
    observableManager.destroy(group);

    assertThat(group.hasObservables(fooObserver)).isEqualTo(false);
  }

  @Test public void shouldRemoveObservablesAfterTermination() throws Exception {
    ObservableGroup group = observableManager.newGroup();
    PublishSubject<String> sourceObservable = PublishSubject.create();
    TestObserver<String> subscriber = new TestObserver<>();
    sourceObservable.compose(group.transform(subscriber)).subscribe(subscriber);

    sourceObservable.onNext("Roberto Gomez Bolanos is king");
    sourceObservable.onComplete();

    subscriber.assertComplete();
    assertThat(group.hasObservables(fooObserver)).isEqualTo(false);
  }

  @Test public void shouldRemoveResponseAfterErrorDelivery() throws InterruptedException {
    ObservableGroup group = observableManager.newGroup();
    TestObserver<String> testObserver = new TestObserver<>();
    PublishSubject<String> sourceObservable = PublishSubject.create();

    sourceObservable.compose(group.transform(testObserver)).subscribe(testObserver);

    sourceObservable.onError(new RuntimeException("BOOM!"));

    testObserver.assertError(Exception.class);

    assertThat(group.hasObservables(fooObserver)).isEqualTo(false);
  }

  @Test public void shouldNotDeliverResultWhileUnsubscribed() throws Exception {
    ObservableGroup group = observableManager.newGroup();
    TestObserver<String> testObserver = new TestObserver<>();
    PublishSubject<String> sourceObservable = PublishSubject.create();

    sourceObservable.compose(group.transform(testObserver)).subscribe(testObserver);
    group.dispose();

    sourceObservable.onNext("Roberto Gomez Bolanos");
    sourceObservable.onComplete();

    testObserver.assertNotComplete();
    assertThat(group.hasObservables(testObserver)).isEqualTo(true);
  }

  @Test public void shouldDeliverQueuedEventsWhenResubscribed() throws Exception {
    ObservableGroup group = observableManager.newGroup();
    TestAutoResubscribingObserver resubscribingObserver = new TestAutoResubscribingObserver("foo");
    PublishSubject<String> sourceObservable = PublishSubject.create();
    sourceObservable.compose(group.transform(resubscribingObserver))
        .subscribe(resubscribingObserver);
    group.dispose();

    sourceObservable.onNext("Hello World");
    sourceObservable.onComplete();

    resubscribingObserver.assertionTarget.assertNotComplete();
    resubscribingObserver.assertionTarget.assertNoValues();

    // TestObserver cannot be reused after being disposed in RxJava2
    resubscribingObserver = new TestAutoResubscribingObserver("foo");
    group.observable(resubscribingObserver).subscribe(resubscribingObserver);

    resubscribingObserver.assertionTarget.assertComplete();
    resubscribingObserver.assertionTarget.assertValue("Hello World");
    assertThat(group.hasObservables(resubscribingObserver)).isEqualTo(false);
  }

  @Test public void shouldDeliverQueuedErrorWhenResubscribed() throws Exception {
    ObservableGroup group = observableManager.newGroup();
    TestAutoResubscribingObserver resubscribingObserver = new TestAutoResubscribingObserver("foo");
    PublishSubject<String> sourceObservable = PublishSubject.create();

    sourceObservable.compose(group.transform(resubscribingObserver))
        .subscribe(resubscribingObserver);
    group.dispose();

    sourceObservable.onError(new Exception("Exploded"));

    resubscribingObserver.assertionTarget.assertNotComplete();
    resubscribingObserver.assertionTarget.assertNoValues();

    resubscribingObserver = new TestAutoResubscribingObserver("foo");
    group.observable(resubscribingObserver).subscribe(resubscribingObserver);

    resubscribingObserver.assertionTarget.assertError(Exception.class);
    assertThat(group.hasObservables(resubscribingObserver)).isEqualTo(false);
  }

  @Test public void shouldNotDeliverEventsWhenResubscribedIfLocked() {
    ObservableGroup group = observableManager.newGroup();
    TestAutoResubscribingObserver testObserver = new TestAutoResubscribingObserver("foo");
    PublishSubject<String> sourceObservable = PublishSubject.create();
    sourceObservable.compose(group.transform(testObserver)).subscribe(testObserver);
    group.dispose();

    sourceObservable.onNext("Hello World");
    sourceObservable.onComplete();

    group.lock();
    testObserver = new TestAutoResubscribingObserver("foo");
    group.observable(testObserver).subscribe(testObserver);

    testObserver.assertionTarget.assertNotComplete();
    testObserver.assertionTarget.assertNoValues();

    group.unlock();
    testObserver.assertionTarget.assertComplete();
    testObserver.assertionTarget.assertNoErrors();
    testObserver.assertionTarget.assertValue("Hello World");
    assertThat(group.hasObservables(testObserver)).isEqualTo(false);
  }

  @Test public void shouldUnsubscribeByContext() throws Exception {
    ObservableGroup group = observableManager.newGroup();
    ObservableGroup group2 = observableManager.newGroup();
    PublishSubject<String> sourceObservable = PublishSubject.create();
    TestObserver<String> testObserver = new TestObserver<>();

    sourceObservable.compose(group2.transform(testObserver)).subscribe(testObserver);
    group.dispose();

    sourceObservable.onNext("Gremio Foot-ball Porto Alegrense");
    sourceObservable.onComplete();

    testObserver.assertComplete();
    testObserver.assertNoErrors();
    testObserver.assertValue("Gremio Foot-ball Porto Alegrense");

    assertThat(group2.hasObservables(fooObserver)).isEqualTo(false);
  }

  @Test public void shouldNotDeliverEventsAfterCancelled() throws Exception {
    ObservableGroup group = observableManager.newGroup();
    PublishSubject<String> sourceObservable = PublishSubject.create();
    TestObserver<String> testObserver = new TestObserver<>();

    sourceObservable.compose(group.transform(testObserver)).subscribe(testObserver);
    observableManager.destroy(group);

    sourceObservable.onNext("Gremio Foot-ball Porto Alegrense");
    sourceObservable.onComplete();

    testObserver.assertNotComplete();
    assertThat(group.hasObservables(fooObserver)).isEqualTo(false);
  }

  @Test public void shouldNotRemoveSubscribersForOtherIds() throws Exception {
    ObservableGroup group = observableManager.newGroup();
    ObservableGroup group2 = observableManager.newGroup();
    PublishSubject<String> subject1 = PublishSubject.create();
    TestAutoResubscribingObserver testSubscriber1 = new TestAutoResubscribingObserver("foo");
    PublishSubject<String> subject2 = PublishSubject.create();
    TestAutoResubscribingObserver testSubscriber2 = new TestAutoResubscribingObserver("bar");

    subject1.compose(group.transform(testSubscriber1)).subscribe(testSubscriber1);
    subject2.compose(group2.transform(testSubscriber2)).subscribe(testSubscriber2);
    group.dispose();

    subject1.onNext("Florinda Mesa");
    subject1.onComplete();
    subject2.onNext("Carlos Villagran");
    subject2.onComplete();

    testSubscriber1.assertionTarget.assertNotComplete();
    testSubscriber2.assertionTarget.assertNoErrors();
    testSubscriber2.assertionTarget.assertValue("Carlos Villagran");
  }

  @Test public void shouldOverrideExistingSubscriber() throws Exception {
    ObservableGroup group = observableManager.newGroup();
    PublishSubject<String> sourceObservable = PublishSubject.create();
    TestAutoResubscribingObserver testSubscriber1 = new TestAutoResubscribingObserver("foo");
    TestAutoResubscribingObserver testSubscriber2 = new TestAutoResubscribingObserver("foo");

    sourceObservable.compose(group.transform(testSubscriber1)).subscribe(testSubscriber1);
    sourceObservable.compose(group.transform(testSubscriber2)).subscribe(testSubscriber2);

    sourceObservable.onNext("Ruben Aguirre");
    sourceObservable.onComplete();

    testSubscriber1.assertionTarget.assertNotComplete();
    testSubscriber1.assertionTarget.assertNoValues();
    testSubscriber2.assertionTarget.assertComplete();
    testSubscriber2.assertionTarget.assertValue("Ruben Aguirre");
  }

  @Test public void shouldQueueMultipleRequests() throws Exception {
    ObservableGroup group = observableManager.newGroup();
    PublishSubject<String> subject1 = PublishSubject.create();
    TestObserver<String> testSubscriber1 = new TestObserver<>();
    PublishSubject<String> subject2 = PublishSubject.create();
    TestObserver<String> testSubscriber2 = new TestObserver<>();

    subject1.compose(group.transform(testSubscriber1)).subscribe(testSubscriber1);
    subject2.compose(group.transform(testSubscriber2)).subscribe(testSubscriber2);
    group.dispose();

    subject1.onNext("Chespirito");
    subject1.onComplete();
    subject2.onNext("Edgar Vivar");
    subject2.onComplete();

    testSubscriber1.assertNotComplete();
    testSubscriber2.assertNotComplete();
    assertThat(group.hasObservables(testSubscriber1)).isEqualTo(true);
    assertThat(group.hasObservables(testSubscriber2)).isEqualTo(true);
  }

  @Test public void shouldNotDeliverResultWhileLocked() throws Exception {
    ObservableGroup group = observableManager.newGroup();
    TestObserver<String> testObserver = new TestObserver<>();
    PublishSubject<String> sourceObservable = PublishSubject.create();

    group.lock();
    sourceObservable.compose(group.transform(testObserver)).subscribe(testObserver);

    sourceObservable.onNext("Chespirito");
    sourceObservable.onComplete();

    testObserver.assertNotComplete();
    testObserver.assertNoValues();
    assertThat(group.hasObservables(testObserver)).isEqualTo(true);
  }

  @Test public void shouldAutoResubscribeAfterUnlock() throws InterruptedException {
    ObservableGroup group = observableManager.newGroup();
    TestObserver<String> testObserver = new TestObserver<>();
    PublishSubject<String> sourceObservable = PublishSubject.create();

    group.lock();
    sourceObservable.compose(group.transform(testObserver)).subscribe(testObserver);

    sourceObservable.onNext("Chespirito");
    sourceObservable.onComplete();

    group.unlock();

    testObserver.assertComplete();
    testObserver.assertNoErrors();
    testObserver.assertValue("Chespirito");
    assertThat(group.hasObservables(fooObserver)).isEqualTo(false);
  }

  @Test public void shouldAutoResubscribeAfterLockAndUnlock() {
    ObservableGroup group = observableManager.newGroup();
    TestObserver<String> testObserver = new TestObserver<>();
    PublishSubject<String> sourceObservable = PublishSubject.create();

    sourceObservable.compose(group.transform(testObserver)).subscribe(testObserver);
    group.lock();

    sourceObservable.onNext("Chespirito");
    sourceObservable.onComplete();

    group.unlock();

    testObserver.assertTerminated();
    testObserver.assertNoErrors();
    testObserver.assertValue("Chespirito");
    assertThat(group.hasObservables(fooObserver)).isEqualTo(false);
  }

  @Test public void testUnsubscribeWhenLocked() {
    ObservableGroup group = observableManager.newGroup();
    TestObserver<String> testObserver = new TestObserver<>();
    PublishSubject<String> sourceObservable = PublishSubject.create();

    sourceObservable.compose(group.transform(testObserver)).subscribe(testObserver);
    group.lock();
    group.dispose();

    sourceObservable.onNext("Chespirito");
    sourceObservable.onComplete();

    group.unlock();

    testObserver.assertNotComplete();
    testObserver.assertNoValues();
    assertThat(group.hasObservables(testObserver)).isEqualTo(true);
  }

  @Test public void testAddThrowsAfterDestroyed() {
    ObservableGroup group = observableManager.newGroup();
    Observable<String> source = PublishSubject.create();
    TestObserver<String> observer = new TestObserver<>();
    group.destroy();
    source.compose(group.transform(observer)).subscribe(observer);
    observer.assertError(IllegalStateException.class);
  }

  @Test public void testResubscribeThrowsAfterDestroyed() {
    ObservableGroup group = observableManager.newGroup();
    Observable<String> source = PublishSubject.create();
    TestObserver<String> observer = new TestObserver<>();
    try {
      source.compose(group.transform(observer)).subscribe(observer);
      group.dispose();
      group.destroy();
      group.observable(fooObserver).subscribe(new TestObserver<>());
      fail();
    } catch (IllegalStateException ignored) {
    }
  }

  @Test public void shouldReplaceObservablesOfSameTagAndSameGroupId() {
    ObservableGroup group = observableManager.newGroup();
    PublishSubject<String> observable1 = PublishSubject.create();
    PublishSubject<String> observable2 = PublishSubject.create();
    TestAutoResubscribingObserver observer1 = new TestAutoResubscribingObserver("foo");
    TestAutoResubscribingObserver observer2 = new TestAutoResubscribingObserver("foo");
    observable1.compose(group.transform(observer1)).subscribe(observer1);
    observable2.compose(group.transform(observer2)).subscribe(observer2);

    assertThat(group.subscription(fooObserver).isCancelled()).isFalse();
    assertThat(group.hasObservables(fooObserver)).isTrue();

    observable1.onNext("Hello World 1");
    observable1.onComplete();

    observable2.onNext("Hello World 2");
    observable2.onComplete();

    observer2.assertionTarget.awaitTerminalEvent();
    observer2.assertionTarget.assertComplete();
    observer2.assertionTarget.assertValue("Hello World 2");

    observer1.assertionTarget.assertNoValues();
  }

  /**
   * The same observable tag can be used so long as it is associated with a different observer tag.
   */
  @Test public void shouldNotReplaceObservableOfSameTagAndSameGroupIdAndDifferentObservers() {
    ObservableGroup group = observableManager.newGroup();
    PublishSubject<String> observable1 = PublishSubject.create();
    TestObserver<String> observer1 = new TestObserver<>();
    TestObserver<String> observer2 = new TestObserver<>();
    String sharedObservableTag = "sharedTag";
    observable1.compose(group.transform(observer1, sharedObservableTag)).subscribe(observer1);
    observable1.compose(group.transform(observer2, sharedObservableTag)).subscribe(observer2);

    assertThat(group.subscription(observer1, sharedObservableTag).isCancelled()).isFalse();
    assertThat(group.hasObservables(observer1)).isTrue();

    assertThat(group.subscription(observer2, sharedObservableTag).isCancelled()).isFalse();
    assertThat(group.hasObservables(observer2)).isTrue();

    observable1.onNext("Hello World 1");
    observable1.onComplete();

    observer2.assertComplete();
    observer2.assertValue("Hello World 1");

    observer1.assertComplete();
    observer1.assertValue("Hello World 1");
  }

  @Test public void testCancelAndReAddSubscription() {
    ObservableGroup group = observableManager.newGroup();
    PublishSubject<String> sourceObservable = PublishSubject.create();
    sourceObservable.compose(group.transform(fooObserver)).subscribe(fooObserver);
    group.cancelAllObservablesForObserver(fooObserver);
    assertThat(group.subscription(fooObserver)).isNull();

    sourceObservable.compose(group.transform(fooObserver)).subscribe(fooObserver);

    assertThat(group.subscription(fooObserver).isCancelled()).isFalse();
  }

  @Test public void testDisposingObserver() {
    ObservableGroup group = observableManager.newGroup();
    TestObserver<String> testObserver = new TestObserver<>();
    PublishSubject<String> sourceObservable = PublishSubject.create();

    sourceObservable.compose(group.transform(testObserver)).subscribe(testObserver);

    testObserver.dispose();

    sourceObservable.onNext("Chespirito");
    testObserver.assertNoValues();

    assertThat(group.hasObservables(testObserver)).isEqualTo(true);
  }

  @Test public void testDisposingObserverResubscribe() {
    ObservableGroup group = observableManager.newGroup();
    TestAutoResubscribingObserver testObserver = new TestAutoResubscribingObserver("foo");
    PublishSubject<String> sourceObservable = PublishSubject.create();

    sourceObservable.compose(group.transform(testObserver)).subscribe(testObserver);

    testObserver.dispose();

    sourceObservable.onNext("Chespirito");
    testObserver.assertionTarget.assertNoValues();

    testObserver = new TestAutoResubscribingObserver("foo");

    group.resubscribe(testObserver);
    testObserver.assertionTarget.assertValue("Chespirito");
  }

}
