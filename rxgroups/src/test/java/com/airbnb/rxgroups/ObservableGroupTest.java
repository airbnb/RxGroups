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

import rx.Observable;
import rx.Observer;
import rx.subjects.PublishSubject;

import static junit.framework.TestCase.fail;
import static org.assertj.core.api.Assertions.assertThat;

public class ObservableGroupTest {
  private final ObservableManager observableManager = new ObservableManager();
  private final TestAutoResubscribingObserver fooObserver =
          new TestAutoResubscribingObserver("foo");
  private final TestAutoResubscribingObserver barObserver =
          new TestAutoResubscribingObserver("bar");

  @Before public void setUp() throws IOException {
    System.setProperty("rxjava.plugin.RxJavaSchedulersHook.implementation",
        TestRxJavaSchedulerHook.class.getName());
  }

  @Test public void shouldNotHaveObservable() {
    ObservableGroup group = observableManager.newGroup();
    assertThat(group.hasObservables(fooObserver)).isEqualTo(false);
  }

  @Test public void shouldAddRequestByObserverTag() {
    ObservableGroup group = observableManager.newGroup();
    ObservableGroup group2 = observableManager.newGroup();
    Observable<String> observable = Observable.never();

    group.add(fooObserver.getTag(), null, observable, new TestObserver<>());

    assertThat(group.hasObservables(fooObserver)).isEqualTo(true);
    assertThat(group2.hasObservables(fooObserver)).isEqualTo(false);
    assertThat(group.hasObservables(barObserver)).isEqualTo(false);
  }

  @Test public void shouldNotBeCompleted() {
    ObservableGroup group = observableManager.newGroup();
    TestObserver<Object> subscriber = new TestObserver<>();
    group.add(fooObserver.getTag(), fooObserver.getTag(), Observable.never(), subscriber);
    subscriber.assertNotCompleted();
  }

  @Test public void shouldBeSubscribed() {
    ObservableGroup group = observableManager.newGroup();
    group.add(fooObserver.getTag(), fooObserver.getTag(), Observable.never(), new TestObserver<>());
    assertThat(group.subscription(fooObserver).isCancelled()).isEqualTo(false);
  }

  @Test public void shouldDeliverSuccessfulEvent() throws Exception {
    ObservableGroup group = observableManager.newGroup();
    PublishSubject<String> subject = PublishSubject.create();
    TestObserver<String> subscriber = new TestObserver<>();

    group.add(fooObserver.getTag(), fooObserver.getTag(), subject, subscriber);
    subscriber.assertNotCompleted();

    subject.onNext("Foo Bar");
    subject.onCompleted();

    subscriber.assertCompleted();
    subscriber.assertValue("Foo Bar");
  }

  @Test public void shouldDeliverError() throws Exception {
    ObservableGroup group = observableManager.newGroup();
    TestObserver<String> testObserver = new TestObserver<>();
    Observable<String> observable = Observable.error(new RuntimeException("boom"));
    group.add(fooObserver.getTag(), fooObserver.getTag(), observable, testObserver);

    testObserver.assertError(RuntimeException.class);
  }

  @Test public void shouldSeparateObservablesByGroupId() {
    ObservableGroup group = observableManager.newGroup();
    ObservableGroup group2 = observableManager.newGroup();
    Observable<String> observable1 = Observable.never();
    Observable<String> observable2 = Observable.never();
    TestObserver<String> subscriber1 = new TestObserver<>();
    TestObserver<String> subscriber2 = new TestObserver<>();

    group.add(barObserver.getTag(), null, observable1, subscriber1);
    assertThat(group.hasObservables(barObserver)).isEqualTo(true);
    assertThat(group.hasObservables(fooObserver)).isEqualTo(false);
    assertThat(group2.hasObservables(barObserver)).isEqualTo(false);
    assertThat(group2.hasObservables(fooObserver)).isEqualTo(false);

    group2.add(fooObserver.getTag(), null, observable2, subscriber2);
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
    TestObserver<String> subscriber1 = new TestObserver<>();

    group.add(fooObserver.getTag(), fooObserver.getTag(), observable1, subscriber1);
    group2.add(fooObserver.getTag(), fooObserver.getTag(), observable2, subscriber1);

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

    group.add(fooObserver.getTag(), fooObserver.getTag(), observable1, subscriber1);
    group.add(barObserver.getTag(), barObserver.getTag(), observable2, subscriber2);

    group.unsubscribe();
    observableManager.destroy(group);

    assertThat(group.hasObservables(fooObserver)).isEqualTo(false);
    assertThat(group.hasObservables(barObserver)).isEqualTo(false);
  }

  @Test public void shouldClearQueuedResults() throws Exception {
    ObservableGroup group = observableManager.newGroup();
    PublishSubject<String> subject = PublishSubject.create();
    TestObserver<String> subscriber1 = new TestObserver<>();

    group.add(fooObserver.getTag(), fooObserver.getTag(), subject, subscriber1);
    group.unsubscribe();
    subject.onNext("Hello");
    subject.onCompleted();
    observableManager.destroy(group);

    assertThat(group.hasObservables(fooObserver)).isEqualTo(false);
  }

  @Test public void shouldRemoveObservablesAfterTermination() throws Exception {
    ObservableGroup group = observableManager.newGroup();
    PublishSubject<String> subject = PublishSubject.create();
    TestObserver<String> subscriber = new TestObserver<>();
    group.add(fooObserver.getTag(), fooObserver.getTag(), subject, subscriber);

    subject.onNext("Roberto Gomez Bolanos is king");
    subject.onCompleted();

    subscriber.assertCompleted();
    assertThat(group.hasObservables(fooObserver)).isEqualTo(false);
  }

  @Test public void shouldRemoveResponseAfterErrorDelivery() throws InterruptedException {
    ObservableGroup group = observableManager.newGroup();
    TestObserver<String> testObserver = new TestObserver<>();
    PublishSubject<String> subject = PublishSubject.create();

    group.add(fooObserver.getTag(), fooObserver.getTag(), subject, testObserver);

    subject.onError(new RuntimeException("BOOM!"));

    testObserver.assertError(Exception.class);

    assertThat(group.hasObservables(fooObserver)).isEqualTo(false);
  }

  @Test public void shouldNotDeliverResultWhileUnsubscribed() throws Exception {
    ObservableGroup group = observableManager.newGroup();
    TestObserver<String> testObserver = new TestObserver<>();
    PublishSubject<String> subject = PublishSubject.create();

    group.add(fooObserver.getTag(), fooObserver.getTag(), subject, testObserver);
    group.unsubscribe();

    subject.onNext("Roberto Gomez Bolanos");
    subject.onCompleted();

    testObserver.assertNotCompleted();
    assertThat(group.hasObservables(fooObserver)).isEqualTo(true);
  }

  @Test public void shouldDeliverQueuedEventsWhenResubscribed() throws Exception {
    ObservableGroup group = observableManager.newGroup();
    TestObserver<String> testObserver = new TestObserver<>();
    PublishSubject<String> subject = PublishSubject.create();
    group.add(fooObserver.getTag(), fooObserver.getTag(), subject, testObserver);
    group.unsubscribe();

    subject.onNext("Hello World");
    subject.onCompleted();

    testObserver.assertNotCompleted();
    testObserver.assertNoValues();

    group.observable(fooObserver).subscribe(testObserver);

    testObserver.assertCompleted();
    testObserver.assertValue("Hello World");
    assertThat(group.hasObservables(fooObserver)).isEqualTo(false);
  }

  @Test public void shouldDeliverQueuedErrorWhenResubscribed() throws Exception {
    ObservableGroup group = observableManager.newGroup();
    TestObserver<String> testObserver = new TestObserver<>();
    PublishSubject<String> subject = PublishSubject.create();

    group.add(fooObserver.getTag(), fooObserver.getTag(), subject, testObserver);
    group.unsubscribe();

    subject.onError(new Exception("Exploded"));

    testObserver.assertNotCompleted();
    testObserver.assertNoValues();

    testObserver = new TestObserver<>();
    group.observable(fooObserver).subscribe(testObserver);

    testObserver.assertError(Exception.class);
    assertThat(group.hasObservables(fooObserver)).isEqualTo(false);
  }

  @Test public void shouldNotDeliverEventsWhenResubscribedIfLocked() {
    ObservableGroup group = observableManager.newGroup();
    TestObserver<String> testObserver = new TestObserver<>();
    PublishSubject<String> subject = PublishSubject.create();
    group.add(fooObserver.getTag(), fooObserver.getTag(), subject, testObserver);
    group.unsubscribe();

    subject.onNext("Hello World");
    subject.onCompleted();

    group.lock();
    testObserver = new TestObserver<>();
    group.observable(fooObserver).subscribe(testObserver);

    testObserver.assertNotCompleted();
    testObserver.assertNoValues();

    group.unlock();
    testObserver.assertCompleted();
    testObserver.assertNoErrors();
    testObserver.assertValue("Hello World");
    assertThat(group.hasObservables(fooObserver)).isEqualTo(false);
  }

  @Test public void shouldUnsubscribeByContext() throws Exception {
    ObservableGroup group = observableManager.newGroup();
    ObservableGroup group2 = observableManager.newGroup();
    PublishSubject<String> subject = PublishSubject.create();
    TestObserver<String> testObserver = new TestObserver<>();

    group2.add(fooObserver.getTag(), fooObserver.getTag(), subject, testObserver);
    group.unsubscribe();

    subject.onNext("Gremio Foot-ball Porto Alegrense");
    subject.onCompleted();

    testObserver.assertCompleted();
    testObserver.assertNoErrors();
    testObserver.assertValue("Gremio Foot-ball Porto Alegrense");

    assertThat(group2.hasObservables(fooObserver)).isEqualTo(false);
  }

  @Test public void shouldNotDeliverEventsAfterCancelled() throws Exception {
    ObservableGroup group = observableManager.newGroup();
    PublishSubject<String> subject = PublishSubject.create();
    TestObserver<String> testObserver = new TestObserver<>();

    group.add(fooObserver.getTag(), fooObserver.getTag(), subject, testObserver);
    observableManager.destroy(group);

    subject.onNext("Gremio Foot-ball Porto Alegrense");
    subject.onCompleted();

    testObserver.assertNotCompleted();
    assertThat(group.hasObservables(fooObserver)).isEqualTo(false);
  }

  @Test public void shouldNotRemoveSubscribersForOtherIds() throws Exception {
    ObservableGroup group = observableManager.newGroup();
    ObservableGroup group2 = observableManager.newGroup();
    PublishSubject<String> subject1 = PublishSubject.create();
    TestObserver<String> testSubscriber1 = new TestObserver<>();
    PublishSubject<String> subject2 = PublishSubject.create();
    TestObserver<String> testSubscriber2 = new TestObserver<>();

    group.add(fooObserver.getTag(), fooObserver.getTag(), subject1, testSubscriber1);
    group2.add(barObserver.getTag(), barObserver.getTag(), subject2, testSubscriber2);
    group.unsubscribe();

    subject1.onNext("Florinda Mesa");
    subject1.onCompleted();
    subject2.onNext("Carlos Villagran");
    subject2.onCompleted();

    testSubscriber1.assertNotCompleted();
    testSubscriber2.assertNoErrors();
    testSubscriber2.assertValue("Carlos Villagran");
  }

  @Test public void shouldOverrideExistingSubscriber() throws Exception {
    ObservableGroup group = observableManager.newGroup();
    PublishSubject<String> subject = PublishSubject.create();
    TestObserver<String> testSubscriber1 = new TestObserver<>();
    TestObserver<String> testSubscriber2 = new TestObserver<>();

    group.add(fooObserver.getTag(), fooObserver.getTag(), subject, testSubscriber1);
    group.observable(fooObserver).subscribe(testSubscriber2);

    subject.onNext("Ruben Aguirre");
    subject.onCompleted();

    testSubscriber1.assertNotCompleted();
    testSubscriber1.assertNoValues();
    testSubscriber2.assertCompleted();
    testSubscriber2.assertValue("Ruben Aguirre");
  }

  @Test public void shouldQueueMultipleRequests() throws Exception {
    ObservableGroup group = observableManager.newGroup();
    PublishSubject<String> subject1 = PublishSubject.create();
    TestObserver<String> testSubscriber1 = new TestObserver<>();
    PublishSubject<String> subject2 = PublishSubject.create();
    TestObserver<String> testSubscriber2 = new TestObserver<>();

    group.add(fooObserver.getTag(), fooObserver.getTag(), subject1, testSubscriber1);
    group.add(barObserver.getTag(), barObserver.getTag(), subject2, testSubscriber2);
    group.unsubscribe();

    subject1.onNext("Chespirito");
    subject1.onCompleted();
    subject2.onNext("Edgar Vivar");
    subject2.onCompleted();

    testSubscriber1.assertNotCompleted();
    testSubscriber2.assertNotCompleted();
    assertThat(group.hasObservables(fooObserver)).isEqualTo(true);
    assertThat(group.hasObservables(barObserver)).isEqualTo(true);
  }

  @Test public void shouldNotDeliverResultWhileLocked() throws Exception {
    ObservableGroup group = observableManager.newGroup();
    TestObserver<String> testObserver = new TestObserver<>();
    PublishSubject<String> subject = PublishSubject.create();

    group.lock();
    group.add(fooObserver.getTag(), fooObserver.getTag(), subject, testObserver);

    subject.onNext("Chespirito");
    subject.onCompleted();

    testObserver.assertNotCompleted();
    testObserver.assertNoValues();
    assertThat(group.hasObservables(fooObserver)).isEqualTo(true);
  }

  @Test public void shouldAutoResubscribeAfterUnlock() throws InterruptedException {
    ObservableGroup group = observableManager.newGroup();
    TestObserver<String> testObserver = new TestObserver<>();
    PublishSubject<String> subject = PublishSubject.create();

    group.lock();
    group.add(fooObserver.getTag(), fooObserver.getTag(), subject, testObserver);

    subject.onNext("Chespirito");
    subject.onCompleted();

    group.unlock();

    testObserver.assertCompleted();
    testObserver.assertNoErrors();
    testObserver.assertValue("Chespirito");
    assertThat(group.hasObservables(fooObserver)).isEqualTo(false);
  }

  @Test public void shouldAutoResubscribeAfterLockAndUnlock() {
    ObservableGroup group = observableManager.newGroup();
    TestObserver<String> testObserver = new TestObserver<>();
    PublishSubject<String> subject = PublishSubject.create();

    group.add(fooObserver.getTag(), fooObserver.getTag(), subject, testObserver);
    group.lock();

    subject.onNext("Chespirito");
    subject.onCompleted();

    group.unlock();

    testObserver.assertTerminalEvent();
    testObserver.assertNoErrors();
    testObserver.assertValue("Chespirito");
    assertThat(group.hasObservables(fooObserver)).isEqualTo(false);
  }

  @Test public void testUnsubscribeWhenLocked() {
    ObservableGroup group = observableManager.newGroup();
    TestObserver<String> testObserver = new TestObserver<>();
    PublishSubject<String> subject = PublishSubject.create();

    group.add(fooObserver.getTag(), fooObserver.getTag(), subject, testObserver);
    group.lock();
    group.unsubscribe();

    subject.onNext("Chespirito");
    subject.onCompleted();

    group.unlock();

    testObserver.assertNotCompleted();
    testObserver.assertNoValues();
    assertThat(group.hasObservables(fooObserver)).isEqualTo(true);
  }

  @Test public void testAddThrowsAfterDestroyed() {
    ObservableGroup group = observableManager.newGroup();
    group.destroy();
    try {
      group.add(fooObserver.getTag(), null, PublishSubject.<String>create(), new TestObserver<>());
      fail();
    } catch (IllegalStateException ignored) {
    }
  }

  @Test public void testResubscribeThrowsAfterDestroyed() {
    ObservableGroup group = observableManager.newGroup();
    try {
      group.add(fooObserver.getTag(), null, PublishSubject.<String>create(), new TestObserver<>());
      group.unsubscribe();
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
    TestObserver<String> observer1 = new TestObserver<>();
    TestObserver<String> observer2 = new TestObserver<>();
    group.add(fooObserver.getTag(), fooObserver.getTag(), observable1, observer1);
    group.add(fooObserver.getTag(), fooObserver.getTag(), observable2, observer2);

    assertThat(group.subscription(fooObserver).isCancelled()).isFalse();
    assertThat(group.hasObservables(fooObserver)).isTrue();

    observable1.onNext("Hello World 1");
    observable1.onCompleted();

    observable2.onNext("Hello World 2");
    observable2.onCompleted();

    observer2.assertCompleted();
    observer2.assertValue("Hello World 2");

    observer1.assertNoValues();
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
    group.add(fooObserver.getTag(), sharedObservableTag, observable1, observer1);
    group.add(barObserver.getTag(), sharedObservableTag, observable1, observer2);

    assertThat(group.subscription(fooObserver, sharedObservableTag).isCancelled()).isFalse();
    assertThat(group.hasObservables(fooObserver)).isTrue();

    assertThat(group.subscription(barObserver, sharedObservableTag).isCancelled()).isFalse();
    assertThat(group.hasObservables(barObserver)).isTrue();

    observable1.onNext("Hello World 1");
    observable1.onCompleted();

    observer2.assertCompleted();
    observer2.assertValue("Hello World 1");

    observer1.assertCompleted();
    observer1.assertValue("Hello World 1");
  }

  /**
   * The same observable tag can be used so long as it is associated with a different observer tag.
   */
  @Test public void testCancelAndRemoveAllWithTag() {
    ObservableGroup group = observableManager.newGroup();
    PublishSubject<String> observable1 = PublishSubject.create();
    TestObserver<String> observer1 = new TestObserver<>();
    TestObserver<String> observer2 = new TestObserver<>();
    String sharedObservableTag = "sharedTag";
    group.add(fooObserver.getTag(), sharedObservableTag, observable1, observer1);
    group.add(barObserver.getTag(), sharedObservableTag, observable1, observer2);

    assertThat(group.subscription(fooObserver, sharedObservableTag).isCancelled()).isFalse();
    assertThat(group.hasObservables(fooObserver)).isTrue();

    assertThat(group.subscription(barObserver, sharedObservableTag).isCancelled()).isFalse();
    assertThat(group.hasObservables(barObserver)).isTrue();

    group.cancelAndRemoveAllWithTag(sharedObservableTag);

    assertThat(group.hasObservables(fooObserver)).isFalse();
    assertThat(group.hasObservables(barObserver)).isFalse();

  }

  @Test public void testCancelAndReAddSubscription() {
    ObservableGroup group = observableManager.newGroup();
    group.add(fooObserver.getTag(), fooObserver.getTag(), PublishSubject.<String>create(),
        new TestObserver<>());
    group.cancelAndRemove(fooObserver);
    assertThat(group.subscription(fooObserver)).isNull();

    Observable<String> observable = PublishSubject.create();
    Observer<String> observer = new TestObserver<>();

    group.add(fooObserver.getTag(), fooObserver.getTag(), observable, observer);

    assertThat(group.subscription(fooObserver).isCancelled()).isFalse();
  }


}
