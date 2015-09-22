package com.airbnb.chimas;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

import rx.Observable;
import rx.Subscriber;
import rx.functions.Action0;
import rx.observers.TestSubscriber;
import rx.subjects.PublishSubject;
import rx.subscriptions.Subscriptions;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

public class SubscriptionProxyTest {
    @Test
    public void testSubscribe() {
        TestSubscriber<Integer> subscriber = new TestSubscriber<>();
        Observable<Integer> observable = Observable.create(new Observable.OnSubscribe<Integer>() {
            @Override
            public void call(Subscriber<? super Integer> subscriber) {
                subscriber.onNext(1234);
                subscriber.onCompleted();
            }
        });
        SubscriptionProxy<Integer> proxy = SubscriptionProxy.create(observable);

        proxy.subscribe(subscriber);

        subscriber.assertCompleted();
        subscriber.assertValue(1234);
    }

    @Test
    public void testUnsubscribe() {
        TestSubscriber<String> subscriber = new TestSubscriber<>();
        PublishSubject<String> subject = PublishSubject.create();
        SubscriptionProxy<String> proxy = SubscriptionProxy.create(subject);

        proxy.subscribe(subscriber);
        proxy.unsubscribe();

        subject.onNext("Avanti!");
        subject.onCompleted();

        assertThat(proxy.isUnsubscribed(), equalTo(true));
        subscriber.awaitTerminalEvent(10, TimeUnit.MILLISECONDS);
        subscriber.assertNotCompleted();
        subscriber.assertNoValues();
    }

    static class TestOnUnsubscribe implements Action0 {
        boolean called = false;

        @Override
        public void call() {
            called = true;
        }
    }

    @Test
    public void testCancelShouldUnsubscribeFromSourceObservable() {
        TestSubscriber<String> subscriber = new TestSubscriber<>();
        final TestOnUnsubscribe onUnsubscribe = new TestOnUnsubscribe();
        Observable<String> observable = Observable.create(new Observable.OnSubscribe<String>() {
            @Override
            public void call(final Subscriber<? super String> subscriber) {
                subscriber.add(Subscriptions.create(onUnsubscribe));
            }
        });
        SubscriptionProxy<String> proxy = SubscriptionProxy.create(observable);

        proxy.subscribe(subscriber);
        proxy.cancel();

        assertThat(proxy.isUnsubscribed(), equalTo(true));
        assertThat(onUnsubscribe.called, equalTo(true));
    }

    @Test
    public void shouldNotRedeliverSameResultsToSameSubscriberAfterResubscription() {
        TestSubscriber<String> subscriber = new TestSubscriber<>();
        PublishSubject<String> subject = PublishSubject.create();
        SubscriptionProxy<String> proxy = SubscriptionProxy.create(subject);

        proxy.subscribe(subscriber);

        subject.onNext("Avanti!");
        subject.onCompleted();

        proxy.unsubscribe();
        proxy.subscribe(subscriber);

        subscriber.assertCompleted();
        subscriber.assertValue("Avanti!");
    }

    @Test
    public void shouldCacheResultsWhileUnsubscribedAndDeliverAfterResubscription() {
        TestSubscriber<String> subscriber = new TestSubscriber<>();
        PublishSubject<String> subject = PublishSubject.create();
        SubscriptionProxy<String> proxy = SubscriptionProxy.create(subject);

        proxy.subscribe(subscriber);
        proxy.unsubscribe();

        subscriber.assertNoValues();

        subject.onNext("Avanti!");
        subject.onCompleted();

        proxy.subscribe(subscriber);

        subscriber.awaitTerminalEvent(3, TimeUnit.SECONDS);
        subscriber.assertValue("Avanti!");
    }

    @Test
    public void shouldRedeliverSameResultsToDifferentSubscriber() {
        // Use case: When rotating an activity, RequestManager will re-subscribe original request's
        // Observable to a new Observer, which is a member of the new activity instance. In this
        // case, we may want to redeliver any previous results (if the request is still being
        // managed by RequestManager).
        TestSubscriber<String> subscriber = new TestSubscriber<>();
        PublishSubject<String> subject = PublishSubject.create();
        SubscriptionProxy<String> proxy = SubscriptionProxy.create(subject);

        proxy.subscribe(subscriber);

        subject.onNext("Avanti!");
        subject.onCompleted();

        proxy.unsubscribe();

        TestSubscriber<String> newSubscriber = new TestSubscriber<>();
        proxy.subscribe(newSubscriber);

        newSubscriber.awaitTerminalEvent(3, TimeUnit.SECONDS);
        newSubscriber.assertCompleted();
        newSubscriber.assertValue("Avanti!");

        subscriber.assertCompleted();
        subscriber.assertValue("Avanti!");
    }
}