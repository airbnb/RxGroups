package com.airbnb.chimas;

import android.support.annotation.NonNull;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import retrofit.TypedRequest;

public class ListenerFuture<T> extends RequestListener<T> implements Future<T> {
  private TypedRequest request;
  private boolean resultReceived = false;

  private T result;
  private NetworkException exception;

  public static <T> ListenerFuture<T> newFuture() {
    return new ListenerFuture<>();
  }

  private ListenerFuture() {
  }

  public void setRequest(TypedRequest request) {
    this.request = request;
  }

  public synchronized boolean cancel(boolean mayInterruptIfRunning) {
    if (request == null) {
      return false;
    } else if (!isDone()) {
      request.cancel();
      return true;
    } else {
      return false;
    }
  }

  public T get() throws InterruptedException, ExecutionException {
    try {
      return doGet(null);
    } catch (TimeoutException var2) {
      throw new AssertionError(var2);
    }
  }

  public T get(long timeout, @NonNull TimeUnit unit)
      throws InterruptedException, ExecutionException, TimeoutException {
    return doGet(TimeUnit.MILLISECONDS.convert(timeout, unit));
  }

  private synchronized T doGet(Long timeoutMs)
      throws InterruptedException, ExecutionException, TimeoutException {
    if (exception != null) {
      throw new ExecutionException(exception);
    }
    if (resultReceived) {
      return result;
    }
    if (timeoutMs == null) {
      wait(0L);
    } else if (timeoutMs > 0L) {
      wait(timeoutMs);
    }
    if (exception != null) {
      throw new ExecutionException(exception);
    }
    if (!resultReceived) {
      throw new TimeoutException();
    }
    return result;
  }

  public boolean isCancelled() {
    return request != null && request.isCancelled();
  }

  public synchronized boolean isDone() {
    return resultReceived || exception != null || isCancelled();
  }

  @Override
  public synchronized void onResponse(T data) {
    resultReceived = true;
    result = data;
    notifyAll();
  }

  @Override
  public synchronized void onErrorResponse(NetworkException error) {
    exception = error;
    notifyAll();
  }

  public NetworkException getException() {
    return exception;
  }

  public T getResult() {
    return result;
  }
}