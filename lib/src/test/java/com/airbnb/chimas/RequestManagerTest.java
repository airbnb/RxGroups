package com.airbnb.chimas;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.MockWebServer;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLog;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import retrofit.JacksonConverterFactory;
import retrofit.Response;
import retrofit.Retrofit;
import retrofit.RxJavaCallAdapterFactory;
import rx.Observer;
import rx.observers.TestSubscriber;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

@Config(sdk = 21, manifest = ChimasTestRunner.MANIFEST_PATH)
@RunWith(ChimasTestRunner.class)
public class RequestManagerTest {
  private static final int GROUP_A = 1;
  private static final int GROUP_B = 2;

  @Rule public MockWebServer server = new MockWebServer();
  @Rule public ExpectedException thrown = ExpectedException.none();

  RequestManager requestManager = new RequestManager();
  OkHttpClient client = new OkHttpClient();
  Retrofit retrofit = new Retrofit.Builder()
      .client(client)
      .baseUrl(server.url("/"))
      .addCallAdapterFactory(RxJavaCallAdapterFactory.create())
      .addConverterFactory(JacksonConverterFactory.create())
      .build();
  Chimas chimas = new Chimas(String.class, retrofit);

  private final TestSubscriber<Response<String>> listenerA = new TestSubscriber<>();
  private final TestSubscriber<Response<String>> listenerB = new TestSubscriber<>();

  private class TestRequestA extends TestRequest {
    public TestRequestA() {
      super(chimas);
    }
  }

  private class TestRequestB extends TestRequest {
    public TestRequestB() {
      super(chimas);
    }
  }

  private RequestSubscription execute(
      int group, TestRequest request, Observer<Response<String>> listener) {
    return requestManager.execute(
        group, request.getClass().getSimpleName(), request.toObservable(), listener);
  }

  private boolean hasObservable(int group, TestRequest request) {
    return requestManager.hasObservable(group, request.getClass().getSimpleName());
  }

  @Before public void setUp() throws IOException {
    ShadowLog.stream = System.out;
    requestManager.cancel(GROUP_A);
    requestManager.cancel(GROUP_B);
  }

  @Test public void shouldNotHaveRequests() {
    assertThat(requestManager.hasObservable(GROUP_A, "TestRequestA"), equalTo(false));
  }

  @Test public void shouldAddRequestById() {
    TestRequestA request = new TestRequestA();

    execute(GROUP_A, request, listenerA);

    assertThat(hasObservable(GROUP_A, request), equalTo(true));
    assertThat(requestManager.hasObservable(GROUP_A, "TestRequestA"), equalTo(true));

    assertThat(hasObservable(GROUP_B, request), equalTo(false));
    assertThat(requestManager.hasObservable(GROUP_A, "AirRequestV2"), equalTo(false));
  }

  @Test public void shouldNotBeCompleted() {
    execute(GROUP_A, new TestRequestA(), listenerA);
    listenerA.assertNotCompleted();
  }

  @Test public void shouldBeSubscribed() {
    RequestSubscription call = execute(GROUP_A, new TestRequestA(), listenerA);
    assertThat(call.isCancelled(), equalTo(false));
  }

  @Test public void shouldDeliverSuccessfulResponse() throws Exception {
    server.enqueue(new MockResponse().setBody("\"Foo Bar\""));

    execute(GROUP_A, new TestRequestA(), listenerA);

    listenerA.assertNotCompleted();

    listenerA.awaitTerminalEvent(3, TimeUnit.SECONDS);
    listenerA.assertCompleted();
    assertThat(listenerA.getOnNextEvents().get(0).body(), equalTo("Foo Bar"));
  }

  @Test public void shouldDeliverError() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(500).setBody("Failed"));

    TestSubscriber<Response<String>> testSubscriber = new TestSubscriber<>();
    execute(GROUP_A, new TestRequestA(), testSubscriber);

    testSubscriber.awaitTerminalEvent(3, TimeUnit.SECONDS);
    testSubscriber.assertError(NetworkException.class);
  }

  @Test public void shouldReplaceRequestsOfSameClassWithSameId() throws Exception {
    server.enqueue(new MockResponse().setBody("\"Hello World\""));
    server.enqueue(new MockResponse().setBody("\"Hello World\""));

    TestRequestA request1 = new TestRequestA();
    TestRequestA request2 = new TestRequestA();

    RequestSubscription call1 = execute(GROUP_A, request1, listenerA);
    RequestSubscription call2 = execute(GROUP_A, request2, listenerB);

    assertThat(call1.isCancelled(), equalTo(true));
    assertThat(call2.isCancelled(), equalTo(false));

    assertThat(requestManager.hasObservable(GROUP_A, "TestRequestA"), equalTo(true));

    listenerB.awaitTerminalEvent(3, TimeUnit.SECONDS);
    listenerB.assertCompleted();
    assertThat(listenerB.getOnNextEvents().get(0).body(), equalTo("Hello World"));
  }

  @Test public void shouldSeparateRequestsById() {
    TestRequest requestA = new TestRequestA();
    TestRequest requestB = new TestRequestB();

    execute(GROUP_A, requestA, listenerA);
    assertThat(hasObservable(GROUP_A, requestA), equalTo(true));
    assertThat(requestManager.hasObservable(GROUP_A, "TestRequestA"), equalTo(true));
    assertThat(hasObservable(GROUP_A, requestB), equalTo(false));
    assertThat(hasObservable(GROUP_B, requestA), equalTo(false));
    assertThat(hasObservable(GROUP_B, requestB), equalTo(false));

    execute(GROUP_B, requestB, listenerB);
    assertThat(hasObservable(GROUP_A, requestA), equalTo(true));
    assertThat(hasObservable(GROUP_A, requestB), equalTo(false));
    assertThat(hasObservable(GROUP_B, requestA), equalTo(false));
    assertThat(hasObservable(GROUP_B, requestB), equalTo(true));
  }

  @Test public void shouldClearResponsesById() {
    TestRequest requestA = new TestRequestA();
    TestRequest requestB = new TestRequestA();

    RequestSubscription callA = execute(GROUP_A, requestA, listenerA);
    RequestSubscription callB = execute(GROUP_B, requestB, listenerA);

    requestManager.cancel(GROUP_A);

    assertThat(hasObservable(GROUP_A, requestA), equalTo(false));
    assertThat(hasObservable(GROUP_B, requestB), equalTo(true));
    assertThat(callA.isCancelled(), equalTo(true));
    assertThat(callB.isCancelled(), equalTo(false));

    requestManager.cancel(GROUP_B);
    assertThat(hasObservable(GROUP_A, requestA), equalTo(false));
    assertThat(hasObservable(GROUP_B, requestB), equalTo(false));
    assertThat(callA.isCancelled(), equalTo(true));
    assertThat(callB.isCancelled(), equalTo(true));
  }

  @Test public void shouldClearResponsesWhenLocked() {
    TestRequest requestA = new TestRequestA();
    TestRequest requestB = new TestRequestB();

    execute(GROUP_A, requestA, listenerA);
    execute(GROUP_A, requestB, listenerA);

    requestManager.unsubscribe(GROUP_A);
    requestManager.cancel(GROUP_A);

    assertThat(hasObservable(GROUP_A, requestA), equalTo(false));
    assertThat(hasObservable(GROUP_A, requestB), equalTo(false));
  }

  @Test public void shouldClearQueuedResults() throws Exception {
    server.enqueue(new MockResponse().setBody("\"Hello World\""));

    TestRequestA requestA = new TestRequestA();
    execute(GROUP_A, requestA, listenerA);

    // If a request is cleared while its response is queued the request should be deleted and
    // the response never delivered
    requestManager.unsubscribe(GROUP_A);
    listenerA.awaitTerminalEvent(3, TimeUnit.SECONDS);
    listenerA.assertNotCompleted();
    requestManager.cancel(GROUP_A);

    assertThat(hasObservable(GROUP_A, requestA), equalTo(false));
  }

  @Test public void shouldRemoveResponseAfterSuccessfulDelivery() throws Exception {
    server.enqueue(new MockResponse().setBody("\"Roberto Gomez Bolanos is king\""));
    TestRequestA request = new TestRequestA();

    execute(GROUP_A, request, listenerA);

    listenerA.awaitTerminalEvent(3, TimeUnit.SECONDS);

    // This allows us to wait for doOnTerminate() to finish, since it runs in the OkHttp
    // dispatcher thread
    client.getDispatcher().getExecutorService().awaitTermination(1, TimeUnit.SECONDS);

    listenerA.assertCompleted();
    assertThat(hasObservable(GROUP_A, request), equalTo(false));
  }

  @Test public void shouldRemoveResponseAfterErrorDelivery() throws InterruptedException {
    server.enqueue(new MockResponse().setResponseCode(500).setBody("BOOM!"));

    TestSubscriber<Response<String>> testSubscriber = new TestSubscriber<>();
    TestRequestA request = new TestRequestA();

    execute(GROUP_A, request, testSubscriber);
    testSubscriber.awaitTerminalEvent(3, TimeUnit.SECONDS);
    testSubscriber.assertError(NetworkException.class);

    // This allows us to wait for doOnTerminate() to finish, since it runs in the OkHttp
    // dispatcher thread
    client.getDispatcher().getExecutorService().awaitTermination(1, TimeUnit.SECONDS);

    assertThat(hasObservable(GROUP_A, request), equalTo(false));
  }

  @Test public void shouldNotDeliverResultWhileUnsubscribed() throws Exception {
    server.enqueue(new MockResponse().setBody("\"Roberto Gomez Bolanos\""));
    TestRequestA request = new TestRequestA();

    execute(GROUP_A, request, listenerA);
    requestManager.unsubscribe(GROUP_A);

    listenerA.awaitTerminalEvent(3, TimeUnit.SECONDS);

    client.getDispatcher().getExecutorService().awaitTermination(1, TimeUnit.SECONDS);

    listenerA.assertNotCompleted();
    assertThat(hasObservable(GROUP_A, request), equalTo(true));
  }

  @Test public void shouldDeliverQueuedResponseWhenResubscribed() throws Exception {
    server.enqueue(new MockResponse().setBody("\"Hello World\""));
    server.enqueue(new MockResponse().setBody("\"Don Ramón\""));

    TestSubscriber<Response<String>> testSubscriber = new TestSubscriber<>();
    TestRequestA request = new TestRequestA();
    execute(GROUP_A, request, testSubscriber);
    requestManager.unsubscribe(GROUP_A);

    listenerA.awaitTerminalEvent(3, TimeUnit.SECONDS);
    listenerA.assertNotCompleted();

    requestManager.resubscribe(GROUP_A, request.getClass().getSimpleName(), testSubscriber);

    testSubscriber.awaitTerminalEvent(3, TimeUnit.SECONDS);
    testSubscriber.assertNoErrors();
    assertThat(testSubscriber.getOnNextEvents().get(0).body(), equalTo("Hello World"));

    client.getDispatcher().getExecutorService().awaitTermination(1, TimeUnit.SECONDS);

    assertThat(hasObservable(GROUP_A, request), equalTo(false));
  }

  @Test public void shouldDeliverQueuedErrorWhenResubscribed() throws Exception {
    server.enqueue(new MockResponse().setResponseCode(500).setBody("Failed"));
    TestSubscriber<Response<String>> testSubscriber = new TestSubscriber<>();
    TestRequestA request = new TestRequestA();

    execute(GROUP_A, request, testSubscriber);
    requestManager.unsubscribe(GROUP_A);
    requestManager.resubscribe(GROUP_A, request.getClass().getSimpleName(), testSubscriber);

    testSubscriber.awaitTerminalEvent(3, TimeUnit.SECONDS);
    testSubscriber.assertError(NetworkException.class);

    client.getDispatcher().getExecutorService().awaitTermination(1, TimeUnit.SECONDS);

    assertThat(hasObservable(GROUP_A, request), equalTo(false));
  }

  @Test public void shouldUnsubscribeByContext() throws Exception {
    server.enqueue(new MockResponse().setBody("\"Gremio Foot-ball Porto Alegrense\""));
    TestSubscriber<Response<String>> testSubscriber = new TestSubscriber<>();
    TestRequestB requestB = new TestRequestB();
    execute(GROUP_B, requestB, testSubscriber);
    requestManager.unsubscribe(GROUP_A);

    testSubscriber.awaitTerminalEvent(3, TimeUnit.SECONDS);
    testSubscriber.assertCompleted();
    testSubscriber.assertNoErrors();
    assertThat(testSubscriber.getOnNextEvents().get(0).body(),
        equalTo("Gremio Foot-ball Porto Alegrense"));

    client.getDispatcher().getExecutorService().awaitTermination(1, TimeUnit.SECONDS);

    assertThat(hasObservable(GROUP_B, requestB), equalTo(false));
  }

  @Test public void shouldClearQueuedResultWhenSameRequestIsAdded() throws Exception {
    server.enqueue(new MockResponse().setBody("\"El Chavo del Ocho\""));
    server.enqueue(new MockResponse().setBody("\"El Chapulin Colorado\""));

    TestRequestA request = new TestRequestA();
    execute(GROUP_A, request, listenerA);
    requestManager.cancel(GROUP_A);

    listenerA.awaitTerminalEvent(3, TimeUnit.SECONDS);
    listenerA.assertNotCompleted();

    TestSubscriber<Response<String>> testSubscriber = new TestSubscriber<>();
    request = new TestRequestA();
    execute(GROUP_A, request, testSubscriber);

    testSubscriber.awaitTerminalEvent(3, TimeUnit.SECONDS);
    testSubscriber.assertNoErrors();
    assertThat(testSubscriber.getOnNextEvents().get(0).body(), equalTo("El Chapulin Colorado"));

    client.getDispatcher().getExecutorService().awaitTermination(1, TimeUnit.SECONDS);

    listenerA.assertNotCompleted();
    assertThat(hasObservable(GROUP_A, request), equalTo(false));
  }

  @Test
  public void shouldNotDeliverResponseWhenCancelled() throws Exception {
    server.enqueue(new MockResponse().setBody("\"Hello World\""));
    TestRequestA request = new TestRequestA();

    execute(GROUP_A, request, listenerA);
    requestManager.cancel(GROUP_A);

    listenerA.awaitTerminalEvent(3, TimeUnit.SECONDS);

    client.getDispatcher().getExecutorService().awaitTermination(1, TimeUnit.SECONDS);

    listenerA.assertNotCompleted();
    assertThat(hasObservable(GROUP_A, request), equalTo(false));
  }

  @Test public void shouldNotRemoveSubscribersForOtherIds() throws Exception {
    server.enqueue(new MockResponse().setBody("\"Carlos Villagran\""));
    server.enqueue(new MockResponse().setBody("\"Florinda Mesa\""));

    TestSubscriber<Response<String>> testSubscriber = new TestSubscriber<>();
    TestRequestA requestA = new TestRequestA();
    TestRequestB requestB = new TestRequestB();

    execute(GROUP_A, requestA, listenerA);
    execute(GROUP_B, requestB, testSubscriber);
    requestManager.unsubscribe(GROUP_A);

    listenerA.awaitTerminalEvent(3, TimeUnit.SECONDS);
    listenerA.assertNotCompleted();

    client.getDispatcher().getExecutorService().awaitTermination(1, TimeUnit.SECONDS);

    testSubscriber.awaitTerminalEvent(3, TimeUnit.SECONDS);
    testSubscriber.assertNoErrors();
    assertThat(testSubscriber.getOnNextEvents().get(0).body(), equalTo("Florinda Mesa"));
  }

  @Test public void shouldOverrideExistingSubscriber() throws Exception {
    server.enqueue(new MockResponse().setBody("\"Ruben Aguirre\""));
    server.enqueue(new MockResponse().setBody("\"Maria Antonieta de las Nieves\""));
    TestRequestA request = new TestRequestA();

    execute(GROUP_A, request, listenerA);
    requestManager.resubscribe(GROUP_A, request.getClass().getSimpleName(), listenerB);

    listenerA.awaitTerminalEvent(3, TimeUnit.SECONDS);
    listenerA.assertNotCompleted();
    listenerB.awaitTerminalEvent(3, TimeUnit.SECONDS);
    listenerB.assertCompleted();
  }

  @Test public void shouldQueueMultipleRequests() throws Exception {
    server.enqueue(new MockResponse().setBody("\"Chespirito\""));
    server.enqueue(new MockResponse().setBody("\"Edgar Vivar\""));

    TestRequestA requestA = new TestRequestA();
    TestRequestB requestB = new TestRequestB();

    execute(GROUP_A, requestA, listenerA);
    execute(GROUP_A, requestB, listenerB);
    requestManager.unsubscribe(GROUP_A);

    listenerA.awaitTerminalEvent(3, TimeUnit.SECONDS);
    listenerA.assertNotCompleted();
    listenerB.awaitTerminalEvent(3, TimeUnit.SECONDS);
    listenerB.assertNotCompleted();

    client.getDispatcher().getExecutorService().awaitTermination(1, TimeUnit.SECONDS);

    assertThat(hasObservable(GROUP_A, requestA), equalTo(true));
    assertThat(hasObservable(GROUP_A, requestB), equalTo(true));
  }

  @Test public void shouldNotDeliverResultWhileLocked() throws Exception {
    server.enqueue(new MockResponse().setBody("\"Chespirito\""));

    TestSubscriber<Response<String>> testSubscriber = new TestSubscriber<>();
    TestRequestA request = new TestRequestA();

    requestManager.lock(GROUP_A);
    execute(GROUP_A, request, testSubscriber);

    testSubscriber.awaitTerminalEvent(3, TimeUnit.SECONDS);

    testSubscriber.assertNotCompleted();
    testSubscriber.assertNoTerminalEvent();

    assertThat(hasObservable(GROUP_A, request), equalTo(true));
  }

  @Test public void shouldDeliverQueuedResponseWhenUnlocked() throws InterruptedException {
    server.enqueue(new MockResponse().setBody("\"Chespirito\""));

    TestSubscriber<Response<String>> testSubscriber = new TestSubscriber<>();
    TestRequestA request = new TestRequestA();

    requestManager.lock(GROUP_A);
    execute(GROUP_A, request, testSubscriber);

    testSubscriber.awaitTerminalEvent(3, TimeUnit.SECONDS);

    requestManager.unlock(GROUP_A);

    testSubscriber.awaitTerminalEvent(3, TimeUnit.SECONDS);

    testSubscriber.assertCompleted();
    testSubscriber.assertNoErrors();
    assertThat(testSubscriber.getOnNextEvents().get(0).body(), equalTo("Chespirito"));

    client.getDispatcher().getExecutorService().awaitTermination(1, TimeUnit.SECONDS);

    assertThat(hasObservable(GROUP_A, request), equalTo(false));
  }
}
