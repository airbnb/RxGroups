package com.airbnb.rxgroups;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import rx.Notification;
import rx.Observer;
import rx.exceptions.CompositeException;

class TestObserver<T> implements Observer<T> {
  private final Observer<T> delegate;
  private final List<T> onNextEvents = new ArrayList<>();
  private final List<Throwable> onErrorEvents = new ArrayList<>();
  private final List<Notification<T>> onCompletedEvents = new ArrayList<>();
  private final CountDownLatch latch = new CountDownLatch(1);

  TestObserver(Observer<T> delegate) {
    this.delegate = delegate;
  }

  @SuppressWarnings("unchecked") TestObserver() {
    this.delegate = (Observer<T>) INERT;
  }

  @Override public void onCompleted() {
    try {
      onCompletedEvents.add(Notification.<T>createOnCompleted());
      delegate.onCompleted();
    } finally {
      latch.countDown();
    }
  }

  /**
   * Get the {@link Notification}s representing each time this observer was notified of sequence
   * completion via {@link #onCompleted}, as a {@link List}.
   *
   * @return a list of Notifications representing calls to this observer's {@link #onCompleted}
   * method
   */
  List<Notification<T>> getOnCompletedEvents() {
    return Collections.unmodifiableList(onCompletedEvents);
  }

  @Override public void onError(Throwable e) {
    try {
      onErrorEvents.add(e);
      delegate.onError(e);
    } finally {
      latch.countDown();
    }
  }

  /**
   * Get the {@link Throwable}s this observer was notified of via {@link #onError} as a {@link
   * List}.
   *
   * @return a list of Throwables passed to this observer's {@link #onError} method
   */
  private List<Throwable> getOnErrorEvents() {
    return Collections.unmodifiableList(onErrorEvents);
  }

  @Override public void onNext(T t) {
    onNextEvents.add(t);
    delegate.onNext(t);
  }

  /**
   * Get the sequence of items observed by this observer, as an ordered {@link List}.
   *
   * @return a list of items observed by this observer, in the order in which they were observed
   */
  List<T> getOnNextEvents() {
    return Collections.unmodifiableList(onNextEvents);
  }

  /**
   * Get a list containing all of the items and notifications received by this observer, where the
   * items will be given as-is, any error notifications will be represented by their {@code
   * Throwable}s, and any sequence-complete notifications will be represented by their {@code
   * Notification} objects.
   *
   * @return a {@link List} containing one item for each item or notification received by this
   * observer, in the order in which they were observed or received
   */
  List<Object> getEvents() {
    ArrayList<Object> events = new ArrayList<>();
    events.add(onNextEvents);
    events.add(onErrorEvents);
    events.add(onCompletedEvents);
    return Collections.unmodifiableList(events);
  }

  /**
   * Assert that a particular sequence of items was received in order.
   *
   * @param items the sequence of items expected to have been observed
   * @throws AssertionError if the sequence of items observed does not exactly match {@code items}
   */
  void assertReceivedOnNext(List<T> items) {
    if (onNextEvents.size() != items.size()) {
      assertionError("Number of items does not match. Provided: " + items.size() + "  Actual: "
          + onNextEvents.size()
          + ".\n"
          + "Provided values: " + items
          + "\n"
          + "Actual values: " + onNextEvents
          + "\n");
    }

    for (int i = 0; i < items.size(); i++) {
      T expected = items.get(i);
      T actual = onNextEvents.get(i);
      if (expected == null) {
        // check for null equality
        if (actual != null) {
          assertionError("Value at index: " + i + " expected to be [null] but was: [" + actual
              + "]\n");
        }
      } else if (!expected.equals(actual)) {
        assertionError("Value at index: " + i
            + " expected to be [" + expected + "] (" + expected.getClass().getSimpleName()
            + ") but was: [" + actual + "] ("
            + (actual != null ? actual.getClass().getSimpleName() : "null") + ")\n");

      }
    }

  }

  /**
   * Assert that a single terminal event occurred, either {@link #onCompleted} or {@link #onError}.
   *
   * @throws AssertionError if not exactly one terminal event notification was received
   */
  void assertTerminalEvent() {
    if (onErrorEvents.size() > 1) {
      assertionError("Too many onError events: " + onErrorEvents.size());
    }

    if (onCompletedEvents.size() > 1) {
      assertionError("Too many onCompleted events: " + onCompletedEvents.size());
    }

    if (onCompletedEvents.size() == 1 && onErrorEvents.size() == 1) {
      assertionError("Received both an onError and onCompleted. Should be one or the other.");
    }

    if (onCompletedEvents.isEmpty() && onErrorEvents.isEmpty()) {
      assertionError("No terminal events received.");
    }
  }

  void assertCompleted() {
    int s = onCompletedEvents.size();
    if (s == 0) {
      assertionError("Not completed!");
    } else if (s > 1) {
      assertionError("Completed multiple times: " + s);
    }
  }

  void assertNoErrors() {
    List<Throwable> onErrorEvents = getOnErrorEvents();
    if (!onErrorEvents.isEmpty()) {
      assertionError("Unexpected onError events");
    }
  }

  void assertNotCompleted() {
    int s = onCompletedEvents.size();
    if (s == 1) {
      assertionError("Completed!");
    } else if (s > 1) {
      assertionError("Completed multiple times: " + s);
    }
  }

  void assertValues(T... values) {
    assertReceivedOnNext(Arrays.asList(values));
  }

  void assertValue(T value) {
    assertReceivedOnNext(Collections.singletonList(value));
  }

  void assertNoValues() {
    int s = getOnNextEvents().size();
    if (s != 0) {
      assertionError("No onNext events expected yet some received: " + s);
    }
  }

  void awaitTerminalEvent(long timeout, TimeUnit unit) {
    try {
      latch.await(timeout, unit);
    } catch (InterruptedException e) {
      throw new IllegalStateException("Interrupted", e);
    }
  }

  void assertError(Class<? extends Throwable> clazz) {
    List<Throwable> err = getOnErrorEvents();
    if (err.isEmpty()) {
      assertionError("No errors");
    } else if (err.size() > 1) {
      AssertionError ae = new AssertionError("Multiple errors: " + err.size());
      ae.initCause(new CompositeException(err));
      throw ae;
    } else if (!clazz.isInstance(err.get(0))) {
      AssertionError ae = new AssertionError("Exceptions differ; expected: " + clazz + ", actual: "
          + err.get(0));
      ae.initCause(err.get(0));
      throw ae;
    }
  }

  /**
   * Combines an assertion error message with the current completion and error state of this
   * TestSubscriber, giving more information when some assertXXX check fails.
   *
   * @param message the message to use for the error
   */
  private void assertionError(String message) {
    StringBuilder b = new StringBuilder(message.length() + 32);

    b.append(message)
        .append(" (");

    int c = onCompletedEvents.size();
    b.append(c)
        .append(" completion");
    if (c != 1) {
      b.append('s');
    }
    b.append(')');

    if (!onErrorEvents.isEmpty()) {
      int size = onErrorEvents.size();
      b.append(" (+")
          .append(size)
          .append(" error");
      if (size != 1) {
        b.append('s');
      }
      b.append(')');
    }

    AssertionError ae = new AssertionError(b.toString());
    if (!onErrorEvents.isEmpty()) {
      if (onErrorEvents.size() == 1) {
        ae.initCause(onErrorEvents.get(0));
      } else {
        ae.initCause(new CompositeException(onErrorEvents));
      }
    }
    throw ae;
  }

  // do nothing ... including swallowing errors
  private static final Observer<Object> INERT = new Observer<Object>() {
    @Override public void onCompleted() {
      // deliberately ignored
    }

    @Override public void onError(Throwable e) {
      // deliberately ignored
    }

    @Override public void onNext(Object t) {
      // deliberately ignored
    }
  };
}
