package com.airbnb.chimas;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import rx.Observable;
import rx.observers.TestSubscriber;
import rx.subjects.PublishSubject;

import static org.assertj.core.api.Assertions.assertThat;

public class RequestManagerTest {
  RequestManager requestManager = new RequestManager();

  @Before public void setUp() throws IOException {
    System.setProperty("rxjava.plugin.RxJavaSchedulersHook.implementation",
        TestRxJavaSchedulerHook.class.getName());
  }

  @Test public void shouldNotHaveObservable() {
    assertThat(requestManager.hasObservable(1, "test")).isEqualTo(false);
  }

  @Test public void shouldAddRequestById() {
    Observable<String> observable = Observable.never();

    requestManager.execute(1, "foo", observable, new TestSubscriber<String>());

    assertThat(requestManager.hasObservable(1, "foo")).isEqualTo(true);
    assertThat(requestManager.hasObservable(2, "foo")).isEqualTo(false);
    assertThat(requestManager.hasObservable(1, "bar")).isEqualTo(false);
  }

  @Test public void shouldNotBeCompleted() {
    TestSubscriber<Object> subscriber = new TestSubscriber<>();
    requestManager.execute(1, "foo", Observable.never(), subscriber);
    subscriber.assertNotCompleted();
  }

  @Test public void shouldBeSubscribed() {
    RequestSubscription subscription = requestManager.execute(
        1, "foo", Observable.never(), new TestSubscriber<>());
    assertThat(subscription.isCancelled()).isEqualTo(false);
  }

  @Test public void shouldDeliverSuccessfulEvent() throws Exception {
    PublishSubject<String> subject = PublishSubject.create();
    TestSubscriber<String> subscriber = new TestSubscriber<>();

    requestManager.execute(1, "foo", subject, subscriber);
    subscriber.assertNotCompleted();

    subject.onNext("Foo Bar");
    subject.onCompleted();

    subscriber.assertCompleted();
    subscriber.assertValue("Foo Bar");
  }

  @Test public void shouldDeliverError() throws Exception {
    TestSubscriber<String> testSubscriber = new TestSubscriber<>();
    Observable<String> observable = Observable.error(new RuntimeException("boom"));
    requestManager.execute(1, "foo", observable, testSubscriber);

    testSubscriber.assertError(RuntimeException.class);
  }

  @Test public void shouldReplaceObservablesOfSameTagAndSameGroupId() throws Exception {
    Observable<String> observable1 = Observable.never();
    PublishSubject<String> observable2 = PublishSubject.create();
    TestSubscriber<String> observer1 = new TestSubscriber<>();
    TestSubscriber<String> observer2 = new TestSubscriber<>();
    RequestSubscription subscription1 = requestManager.execute(1, "foo", observable1, observer1);
    RequestSubscription subscription2 = requestManager.execute(1, "foo", observable2, observer2);

    assertThat(subscription1.isCancelled()).isEqualTo(true);
    assertThat(subscription2.isCancelled()).isEqualTo(false);
    assertThat(requestManager.hasObservable(1, "foo")).isEqualTo(true);

    observable2.onNext("Hello World");
    observable2.onCompleted();

    observer2.assertCompleted();
    observer2.assertValue("Hello World");
  }

  @Test public void shouldSeparateObservablesByGroupId() {
    Observable<String> observable1 = Observable.never();
    Observable<String> observable2 = Observable.never();
    TestSubscriber<String> subscriber1 = new TestSubscriber<>();
    TestSubscriber<String> subscriber2 = new TestSubscriber<>();

    requestManager.execute(1, "tag", observable1, subscriber1);
    assertThat(requestManager.hasObservable(1, "tag")).isEqualTo(true);
    assertThat(requestManager.hasObservable(1, "foo")).isEqualTo(false);
    assertThat(requestManager.hasObservable(2, "tag")).isEqualTo(false);
    assertThat(requestManager.hasObservable(2, "foo")).isEqualTo(false);

    requestManager.execute(2, "foo", observable2, subscriber2);
    assertThat(requestManager.hasObservable(1, "tag")).isEqualTo(true);
    assertThat(requestManager.hasObservable(1, "foo")).isEqualTo(false);
    assertThat(requestManager.hasObservable(2, "tag")).isEqualTo(false);
    assertThat(requestManager.hasObservable(2, "foo")).isEqualTo(true);
  }

  @Test public void shouldClearObservablesByGroupId() {
    Observable<String> observable1 = Observable.never();
    Observable<String> observable2 = Observable.never();
    TestSubscriber<String> subscriber1 = new TestSubscriber<>();

    RequestSubscription subscription1 = requestManager.execute(1, "foo", observable1, subscriber1);
    RequestSubscription subscription2 = requestManager.execute(2, "foo", observable2, subscriber1);

    requestManager.cancel(1);

    assertThat(requestManager.hasObservable(1, "foo")).isEqualTo(false);
    assertThat(requestManager.hasObservable(2, "foo")).isEqualTo(true);
    assertThat(subscription1.isCancelled()).isEqualTo(true);
    assertThat(subscription2.isCancelled()).isEqualTo(false);

    requestManager.cancel(2);
    assertThat(requestManager.hasObservable(1, "foo")).isEqualTo(false);
    assertThat(requestManager.hasObservable(2, "foo")).isEqualTo(false);
    assertThat(subscription1.isCancelled()).isEqualTo(true);
    assertThat(subscription2.isCancelled()).isEqualTo(true);
  }

  @Test public void shouldClearObservablesWhenLocked() {
    Observable<String> observable1 = Observable.never();
    Observable<String> observable2 = Observable.never();
    TestSubscriber<String> subscriber1 = new TestSubscriber<>();
    TestSubscriber<String> subscriber2 = new TestSubscriber<>();

    requestManager.execute(1, "foo", observable1, subscriber1);
    requestManager.execute(1, "bar", observable2, subscriber2);

    requestManager.unsubscribe(1);
    requestManager.cancel(1);

    assertThat(requestManager.hasObservable(1, "foo")).isEqualTo(false);
    assertThat(requestManager.hasObservable(1, "bar")).isEqualTo(false);
  }

  @Test public void shouldClearQueuedResults() throws Exception {
    PublishSubject<String> subject = PublishSubject.create();
    TestSubscriber<String> subscriber1 = new TestSubscriber<>();

    requestManager.execute(1, "foo", subject, subscriber1);
    requestManager.unsubscribe(1);
    subject.onNext("Hello");
    subject.onCompleted();
    requestManager.cancel(1);

    assertThat(requestManager.hasObservable(1, "foo")).isEqualTo(false);
  }

  @Test public void shouldRemoveObservablesAfterTermination() throws Exception {
    PublishSubject<String> subject = PublishSubject.create();
    TestSubscriber<String> subscriber = new TestSubscriber<>();
    requestManager.execute(1, "foo", subject, subscriber);

    subject.onNext("Roberto Gomez Bolanos is king");
    subject.onCompleted();

    subscriber.assertCompleted();
    assertThat(requestManager.hasObservable(1, "foo")).isEqualTo(false);
  }

  @Test public void shouldRemoveResponseAfterErrorDelivery() throws InterruptedException {
    TestSubscriber<String> testSubscriber = new TestSubscriber<>();
    PublishSubject<String> subject = PublishSubject.create();

    requestManager.execute(1, "foo", subject, testSubscriber);

    subject.onError(new RuntimeException("BOOM!"));

    testSubscriber.assertError(Exception.class);

    assertThat(requestManager.hasObservable(1, "foo")).isEqualTo(false);
  }

  @Test public void shouldNotDeliverResultWhileUnsubscribed() throws Exception {
    TestSubscriber<String> testSubscriber = new TestSubscriber<>();
    PublishSubject<String> subject = PublishSubject.create();

    requestManager.execute(1, "foo", subject, testSubscriber);
    requestManager.unsubscribe(1);

    subject.onNext("Roberto Gomez Bolanos");
    subject.onCompleted();

    testSubscriber.assertNotCompleted();
    assertThat(requestManager.hasObservable(1, "foo")).isEqualTo(true);
  }

  @Test public void shouldDeliverQueuedEventsWhenResubscribed() throws Exception {
    TestSubscriber<String> testSubscriber = new TestSubscriber<>();
    PublishSubject<String> subject = PublishSubject.create();
    requestManager.execute(1, "foo", subject, testSubscriber);
    requestManager.unsubscribe(1);

    subject.onNext("Hello World");
    subject.onCompleted();

    testSubscriber.assertNotCompleted();
    testSubscriber.assertNoValues();

    requestManager.resubscribe(1, "foo", testSubscriber);

    testSubscriber.assertCompleted();
    testSubscriber.assertNoErrors();
    testSubscriber.assertValue("Hello World");

    assertThat(requestManager.hasObservable(1, "foo")).isEqualTo(false);
  }

  @Test public void shouldDeliverQueuedErrorWhenResubscribed() throws Exception {
    TestSubscriber<String> testSubscriber = new TestSubscriber<>();
    PublishSubject<String> subject = PublishSubject.create();

    requestManager.execute(1, "foo", subject, testSubscriber);
    requestManager.unsubscribe(1);

    subject.onError(new Exception("Exploded"));

    testSubscriber.assertNotCompleted();
    testSubscriber.assertNoValues();

    requestManager.resubscribe(1, "foo", testSubscriber);

    testSubscriber.assertError(Exception.class);

    assertThat(requestManager.hasObservable(1, "foo")).isEqualTo(false);
  }

  @Test public void shouldUnsubscribeByContext() throws Exception {
    PublishSubject<String> subject = PublishSubject.create();
    TestSubscriber<String> testSubscriber = new TestSubscriber<>();

    requestManager.execute(2, "foo", subject, testSubscriber);
    requestManager.unsubscribe(1);

    subject.onNext("Gremio Foot-ball Porto Alegrense");
    subject.onCompleted();

    testSubscriber.assertCompleted();
    testSubscriber.assertNoErrors();
    testSubscriber.assertValue("Gremio Foot-ball Porto Alegrense");

    assertThat(requestManager.hasObservable(2, "foo")).isEqualTo(false);
  }

  @Test public void shouldNotDeliverEventsAfterCancelled() throws Exception {
    PublishSubject<String> subject = PublishSubject.create();
    TestSubscriber<String> testSubscriber = new TestSubscriber<>();

    requestManager.execute(1, "foo", subject, testSubscriber);
    requestManager.cancel(1);

    subject.onNext("Gremio Foot-ball Porto Alegrense");
    subject.onCompleted();

    testSubscriber.assertNotCompleted();
    assertThat(requestManager.hasObservable(1, "foo")).isEqualTo(false);
  }

  @Test public void shouldNotRemoveSubscribersForOtherIds() throws Exception {
    PublishSubject<String> subject1 = PublishSubject.create();
    TestSubscriber<String> testSubscriber1 = new TestSubscriber<>();
    PublishSubject<String> subject2 = PublishSubject.create();
    TestSubscriber<String> testSubscriber2 = new TestSubscriber<>();

    requestManager.execute(1, "foo", subject1, testSubscriber1);
    requestManager.execute(2, "bar", subject2, testSubscriber2);
    requestManager.unsubscribe(1);

    subject1.onNext("Florinda Mesa");
    subject1.onCompleted();
    subject2.onNext("Carlos Villagran");
    subject2.onCompleted();

    testSubscriber1.assertNotCompleted();
    testSubscriber2.assertNoErrors();
    testSubscriber2.assertValue("Carlos Villagran");
  }

  @Test public void shouldOverrideExistingSubscriber() throws Exception {
    PublishSubject<String> subject = PublishSubject.create();
    TestSubscriber<String> testSubscriber1 = new TestSubscriber<>();
    TestSubscriber<String> testSubscriber2 = new TestSubscriber<>();

    requestManager.execute(1, "tag", subject, testSubscriber1);
    requestManager.resubscribe(1, "tag", testSubscriber2);

    subject.onNext("Ruben Aguirre");
    subject.onCompleted();

    testSubscriber1.assertNotCompleted();
    testSubscriber1.assertNoValues();
    testSubscriber2.assertCompleted();
    testSubscriber2.assertValue("Ruben Aguirre");
  }

  @Test public void shouldQueueMultipleRequests() throws Exception {
    PublishSubject<String> subject1 = PublishSubject.create();
    TestSubscriber<String> testSubscriber1 = new TestSubscriber<>();
    PublishSubject<String> subject2 = PublishSubject.create();
    TestSubscriber<String> testSubscriber2 = new TestSubscriber<>();

    requestManager.execute(1, "foo", subject1, testSubscriber1);
    requestManager.execute(1, "bar", subject2, testSubscriber2);
    requestManager.unsubscribe(1);

    subject1.onNext("Chespirito");
    subject1.onCompleted();
    subject2.onNext("Edgar Vivar");
    subject2.onCompleted();

    testSubscriber1.assertNotCompleted();
    testSubscriber2.assertNotCompleted();
    assertThat(requestManager.hasObservable(1, "foo")).isEqualTo(true);
    assertThat(requestManager.hasObservable(1, "bar")).isEqualTo(true);
  }

  @Test public void shouldNotDeliverResultWhileLocked() throws Exception {
    TestSubscriber<String> testSubscriber = new TestSubscriber<>();
    PublishSubject<String> subject = PublishSubject.create();

    requestManager.lock(1);
    requestManager.execute(1, "tag", subject, testSubscriber);

    subject.onNext("Chespirito");
    subject.onCompleted();

    testSubscriber.assertNotCompleted();
    testSubscriber.assertNoValues();
    assertThat(requestManager.hasObservable(1, "tag")).isEqualTo(true);
  }

  @Test public void shouldAutoResubscribeAfterUnlock() throws InterruptedException {
    TestSubscriber<String> testSubscriber = new TestSubscriber<>();
    PublishSubject<String> subject = PublishSubject.create();

    requestManager.lock(1);
    requestManager.execute(1, "tag", subject, testSubscriber);

    subject.onNext("Chespirito");
    subject.onCompleted();

    requestManager.unlock(1);

    testSubscriber.assertCompleted();
    testSubscriber.assertNoErrors();
    testSubscriber.assertValue("Chespirito");
    assertThat(requestManager.hasObservable(1, "tag")).isEqualTo(false);
  }

  @Test public void shouldAutoResubscribeAfterLockAndUnlock() {
    TestSubscriber<String> testSubscriber = new TestSubscriber<>();
    PublishSubject<String> subject = PublishSubject.create();

    requestManager.execute(1, "tag", subject, testSubscriber);
    requestManager.lock(1);

    subject.onNext("Chespirito");
    subject.onCompleted();

    requestManager.unlock(1);

    testSubscriber.assertCompleted();
    testSubscriber.assertNoErrors();
    testSubscriber.assertValue("Chespirito");
    assertThat(requestManager.hasObservable(1, "tag")).isEqualTo(false);
  }

  @Test public void testUnsubscribeWhenLocked() {
    TestSubscriber<String> testSubscriber = new TestSubscriber<>();
    PublishSubject<String> subject = PublishSubject.create();

    requestManager.execute(1, "tag", subject, testSubscriber);
    requestManager.lock(1);
    requestManager.unsubscribe(1);

    subject.onNext("Chespirito");
    subject.onCompleted();

    requestManager.unlock(1);

    testSubscriber.assertNotCompleted();
    testSubscriber.assertNoValues();
    assertThat(requestManager.hasObservable(1, "tag")).isEqualTo(true);
  }
}
