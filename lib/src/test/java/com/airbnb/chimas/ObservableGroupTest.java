package com.airbnb.chimas;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import rx.Observable;
import rx.observers.TestSubscriber;
import rx.subjects.PublishSubject;

import static junit.framework.TestCase.fail;
import static org.assertj.core.api.Assertions.assertThat;

public class ObservableGroupTest {
  ObservableManager observableManager = new ObservableManager();

  @Before public void setUp() throws IOException {
    System.setProperty("rxjava.plugin.RxJavaSchedulersHook.implementation",
        TestRxJavaSchedulerHook.class.getName());
  }

  @Test public void shouldNotHaveObservable() {
    ObservableGroup group = observableManager.newGroup();
    assertThat(group.hasObservable("test")).isEqualTo(false);
  }

  @Test public void shouldAddRequestById() {
    ObservableGroup group = observableManager.newGroup();
    ObservableGroup group2 = observableManager.newGroup();
    Observable<String> observable = Observable.never();

    group.add("foo", observable, new TestSubscriber<String>());

    assertThat(group.hasObservable("foo")).isEqualTo(true);
    assertThat(group2.hasObservable("foo")).isEqualTo(false);
    assertThat(group.hasObservable("bar")).isEqualTo(false);
  }

  @Test public void shouldNotBeCompleted() {
    ObservableGroup group = observableManager.newGroup();
    TestSubscriber<Object> subscriber = new TestSubscriber<>();
    group.add("foo", Observable.never(), subscriber);
    subscriber.assertNotCompleted();
  }

  @Test public void shouldBeSubscribed() {
    ObservableGroup group = observableManager.newGroup();
    RequestSubscription subscription = group.add(
        "foo", Observable.never(), new TestSubscriber<>());
    assertThat(subscription.isCancelled()).isEqualTo(false);
  }

  @Test public void shouldDeliverSuccessfulEvent() throws Exception {
    ObservableGroup group = observableManager.newGroup();
    PublishSubject<String> subject = PublishSubject.create();
    TestSubscriber<String> subscriber = new TestSubscriber<>();

    group.add("foo", subject, subscriber);
    subscriber.assertNotCompleted();

    subject.onNext("Foo Bar");
    subject.onCompleted();

    subscriber.assertCompleted();
    subscriber.assertValue("Foo Bar");
  }

  @Test public void shouldDeliverError() throws Exception {
    ObservableGroup group = observableManager.newGroup();
    TestSubscriber<String> testSubscriber = new TestSubscriber<>();
    Observable<String> observable = Observable.error(new RuntimeException("boom"));
    group.add("foo", observable, testSubscriber);

    testSubscriber.assertError(RuntimeException.class);
  }

  @Test public void shouldReplaceObservablesOfSameTagAndSameGroupId() throws Exception {
    ObservableGroup group = observableManager.newGroup();
    Observable<String> observable1 = Observable.never();
    PublishSubject<String> observable2 = PublishSubject.create();
    TestSubscriber<String> observer1 = new TestSubscriber<>();
    TestSubscriber<String> observer2 = new TestSubscriber<>();
    RequestSubscription subscription1 = group.add("foo", observable1, observer1);
    RequestSubscription subscription2 = group.add("foo", observable2, observer2);

    assertThat(subscription1.isCancelled()).isEqualTo(true);
    assertThat(subscription2.isCancelled()).isEqualTo(false);
    assertThat(group.hasObservable("foo")).isEqualTo(true);

    observable2.onNext("Hello World");
    observable2.onCompleted();

    observer2.assertCompleted();
    observer2.assertValue("Hello World");
  }

  @Test public void shouldSeparateObservablesByGroupId() {
    ObservableGroup group = observableManager.newGroup();
    ObservableGroup group2 = observableManager.newGroup();
    Observable<String> observable1 = Observable.never();
    Observable<String> observable2 = Observable.never();
    TestSubscriber<String> subscriber1 = new TestSubscriber<>();
    TestSubscriber<String> subscriber2 = new TestSubscriber<>();

    group.add("tag", observable1, subscriber1);
    assertThat(group.hasObservable("tag")).isEqualTo(true);
    assertThat(group.hasObservable("foo")).isEqualTo(false);
    assertThat(group2.hasObservable("tag")).isEqualTo(false);
    assertThat(group2.hasObservable("foo")).isEqualTo(false);

    group2.add("foo", observable2, subscriber2);
    assertThat(group.hasObservable("tag")).isEqualTo(true);
    assertThat(group.hasObservable("foo")).isEqualTo(false);
    assertThat(group2.hasObservable("tag")).isEqualTo(false);
    assertThat(group2.hasObservable("foo")).isEqualTo(true);
  }

  @Test public void shouldClearObservablesByGroupId() {
    ObservableGroup group = observableManager.newGroup();
    ObservableGroup group2 = observableManager.newGroup();
    Observable<String> observable1 = Observable.never();
    Observable<String> observable2 = Observable.never();
    TestSubscriber<String> subscriber1 = new TestSubscriber<>();

    RequestSubscription subscription1 = group.add("foo", observable1, subscriber1);
    RequestSubscription subscription2 = group2.add("foo", observable2, subscriber1);

    observableManager.destroy(group);

    assertThat(group.hasObservable("foo")).isEqualTo(false);
    assertThat(group2.hasObservable("foo")).isEqualTo(true);
    assertThat(subscription1.isCancelled()).isEqualTo(true);
    assertThat(subscription2.isCancelled()).isEqualTo(false);

    observableManager.destroy(group2);
    assertThat(group.hasObservable("foo")).isEqualTo(false);
    assertThat(group2.hasObservable("foo")).isEqualTo(false);
    assertThat(subscription1.isCancelled()).isEqualTo(true);
    assertThat(subscription2.isCancelled()).isEqualTo(true);
  }

  @Test public void shouldClearObservablesWhenLocked() {
    ObservableGroup group = observableManager.newGroup();
    Observable<String> observable1 = Observable.never();
    Observable<String> observable2 = Observable.never();
    TestSubscriber<String> subscriber1 = new TestSubscriber<>();
    TestSubscriber<String> subscriber2 = new TestSubscriber<>();

    group.add("foo", observable1, subscriber1);
    group.add("bar", observable2, subscriber2);

    group.unsubscribe();
    observableManager.destroy(group);

    assertThat(group.hasObservable("foo")).isEqualTo(false);
    assertThat(group.hasObservable("bar")).isEqualTo(false);
  }

  @Test public void shouldClearQueuedResults() throws Exception {
    ObservableGroup group = observableManager.newGroup();
    PublishSubject<String> subject = PublishSubject.create();
    TestSubscriber<String> subscriber1 = new TestSubscriber<>();

    group.add("foo", subject, subscriber1);
    group.unsubscribe();
    subject.onNext("Hello");
    subject.onCompleted();
    observableManager.destroy(group);

    assertThat(group.hasObservable("foo")).isEqualTo(false);
  }

  @Test public void shouldRemoveObservablesAfterTermination() throws Exception {
    ObservableGroup group = observableManager.newGroup();
    PublishSubject<String> subject = PublishSubject.create();
    TestSubscriber<String> subscriber = new TestSubscriber<>();
    group.add("foo", subject, subscriber);

    subject.onNext("Roberto Gomez Bolanos is king");
    subject.onCompleted();

    subscriber.assertCompleted();
    assertThat(group.hasObservable("foo")).isEqualTo(false);
  }

  @Test public void shouldRemoveResponseAfterErrorDelivery() throws InterruptedException {
    ObservableGroup group = observableManager.newGroup();
    TestSubscriber<String> testSubscriber = new TestSubscriber<>();
    PublishSubject<String> subject = PublishSubject.create();

    group.add("foo", subject, testSubscriber);

    subject.onError(new RuntimeException("BOOM!"));

    testSubscriber.assertError(Exception.class);

    assertThat(group.hasObservable("foo")).isEqualTo(false);
  }

  @Test public void shouldNotDeliverResultWhileUnsubscribed() throws Exception {
    ObservableGroup group = observableManager.newGroup();
    TestSubscriber<String> testSubscriber = new TestSubscriber<>();
    PublishSubject<String> subject = PublishSubject.create();

    group.add("foo", subject, testSubscriber);
    group.unsubscribe();

    subject.onNext("Roberto Gomez Bolanos");
    subject.onCompleted();

    testSubscriber.assertNotCompleted();
    assertThat(group.hasObservable("foo")).isEqualTo(true);
  }

  @Test public void shouldDeliverQueuedEventsWhenResubscribed() throws Exception {
    ObservableGroup group = observableManager.newGroup();
    TestSubscriber<String> testSubscriber = new TestSubscriber<>();
    PublishSubject<String> subject = PublishSubject.create();
    group.add("foo", subject, testSubscriber);
    group.unsubscribe();

    subject.onNext("Hello World");
    subject.onCompleted();

    testSubscriber.assertNotCompleted();
    testSubscriber.assertNoValues();

    group.resubscribe("foo", testSubscriber);

    testSubscriber.assertCompleted();
    testSubscriber.assertNoErrors();
    testSubscriber.assertValue("Hello World");
    assertThat(group.hasObservable("foo")).isEqualTo(false);
  }

  @Test public void shouldDeliverQueuedErrorWhenResubscribed() throws Exception {
    ObservableGroup group = observableManager.newGroup();
    TestSubscriber<String> testSubscriber = new TestSubscriber<>();
    PublishSubject<String> subject = PublishSubject.create();

    group.add("foo", subject, testSubscriber);
    group.unsubscribe();

    subject.onError(new Exception("Exploded"));

    testSubscriber.assertNotCompleted();
    testSubscriber.assertNoValues();

    group.resubscribe("foo", testSubscriber);

    testSubscriber.assertError(Exception.class);
    assertThat(group.hasObservable("foo")).isEqualTo(false);
  }

  @Test public void shouldUnsubscribeByContext() throws Exception {
    ObservableGroup group = observableManager.newGroup();
    ObservableGroup group2 = observableManager.newGroup();
    PublishSubject<String> subject = PublishSubject.create();
    TestSubscriber<String> testSubscriber = new TestSubscriber<>();

    group2.add("foo", subject, testSubscriber);
    group.unsubscribe();

    subject.onNext("Gremio Foot-ball Porto Alegrense");
    subject.onCompleted();

    testSubscriber.assertCompleted();
    testSubscriber.assertNoErrors();
    testSubscriber.assertValue("Gremio Foot-ball Porto Alegrense");

    assertThat(group2.hasObservable("foo")).isEqualTo(false);
  }

  @Test public void shouldNotDeliverEventsAfterCancelled() throws Exception {
    ObservableGroup group = observableManager.newGroup();
    PublishSubject<String> subject = PublishSubject.create();
    TestSubscriber<String> testSubscriber = new TestSubscriber<>();

    group.add("foo", subject, testSubscriber);
    observableManager.destroy(group);

    subject.onNext("Gremio Foot-ball Porto Alegrense");
    subject.onCompleted();

    testSubscriber.assertNotCompleted();
    assertThat(group.hasObservable("foo")).isEqualTo(false);
  }

  @Test public void shouldNotRemoveSubscribersForOtherIds() throws Exception {
    ObservableGroup group = observableManager.newGroup();
    ObservableGroup group2 = observableManager.newGroup();
    PublishSubject<String> subject1 = PublishSubject.create();
    TestSubscriber<String> testSubscriber1 = new TestSubscriber<>();
    PublishSubject<String> subject2 = PublishSubject.create();
    TestSubscriber<String> testSubscriber2 = new TestSubscriber<>();

    group.add("foo", subject1, testSubscriber1);
    group2.add("bar", subject2, testSubscriber2);
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
    TestSubscriber<String> testSubscriber1 = new TestSubscriber<>();
    TestSubscriber<String> testSubscriber2 = new TestSubscriber<>();

    group.add("tag", subject, testSubscriber1);
    group.resubscribe("tag", testSubscriber2);

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
    TestSubscriber<String> testSubscriber1 = new TestSubscriber<>();
    PublishSubject<String> subject2 = PublishSubject.create();
    TestSubscriber<String> testSubscriber2 = new TestSubscriber<>();

    group.add("foo", subject1, testSubscriber1);
    group.add("bar", subject2, testSubscriber2);
    group.unsubscribe();

    subject1.onNext("Chespirito");
    subject1.onCompleted();
    subject2.onNext("Edgar Vivar");
    subject2.onCompleted();

    testSubscriber1.assertNotCompleted();
    testSubscriber2.assertNotCompleted();
    assertThat(group.hasObservable("foo")).isEqualTo(true);
    assertThat(group.hasObservable("bar")).isEqualTo(true);
  }

  @Test public void shouldNotDeliverResultWhileLocked() throws Exception {
    ObservableGroup group = observableManager.newGroup();
    TestSubscriber<String> testSubscriber = new TestSubscriber<>();
    PublishSubject<String> subject = PublishSubject.create();

    group.lock();
    group.add("tag", subject, testSubscriber);

    subject.onNext("Chespirito");
    subject.onCompleted();

    testSubscriber.assertNotCompleted();
    testSubscriber.assertNoValues();
    assertThat(group.hasObservable("tag")).isEqualTo(true);
  }

  @Test public void shouldAutoResubscribeAfterUnlock() throws InterruptedException {
    ObservableGroup group = observableManager.newGroup();
    TestSubscriber<String> testSubscriber = new TestSubscriber<>();
    PublishSubject<String> subject = PublishSubject.create();

    group.lock();
    group.add("tag", subject, testSubscriber);

    subject.onNext("Chespirito");
    subject.onCompleted();

    group.unlock();

    testSubscriber.assertCompleted();
    testSubscriber.assertNoErrors();
    testSubscriber.assertValue("Chespirito");
    assertThat(group.hasObservable("tag")).isEqualTo(false);
  }

  @Test public void shouldAutoResubscribeAfterLockAndUnlock() {
    ObservableGroup group = observableManager.newGroup();
    TestSubscriber<String> testSubscriber = new TestSubscriber<>();
    PublishSubject<String> subject = PublishSubject.create();

    group.add("tag", subject, testSubscriber);
    group.lock();

    subject.onNext("Chespirito");
    subject.onCompleted();

    group.unlock();

    testSubscriber.assertCompleted();
    testSubscriber.assertNoErrors();
    testSubscriber.assertValue("Chespirito");
    assertThat(group.hasObservable("tag")).isEqualTo(false);
  }

  @Test public void testUnsubscribeWhenLocked() {
    ObservableGroup group = observableManager.newGroup();
    TestSubscriber<String> testSubscriber = new TestSubscriber<>();
    PublishSubject<String> subject = PublishSubject.create();

    group.add("tag", subject, testSubscriber);
    group.lock();
    group.unsubscribe();

    subject.onNext("Chespirito");
    subject.onCompleted();

    group.unlock();

    testSubscriber.assertNotCompleted();
    testSubscriber.assertNoValues();
    assertThat(group.hasObservable("tag")).isEqualTo(true);
  }

  @Test public void testAddThrowsAfterDestroyed() {
    ObservableGroup group = observableManager.newGroup();
    group.destroy();
    try {
      TestSubscriber<String> testSubscriber = new TestSubscriber<>();
      PublishSubject<String> subject = PublishSubject.create();

      group.add("tag", subject, testSubscriber);
      fail();
    } catch (IllegalStateException ignored) {
    }
  }
}
