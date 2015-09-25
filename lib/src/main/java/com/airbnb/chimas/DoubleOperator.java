package com.airbnb.chimas;

import android.util.ArrayMap;

import com.squareup.okhttp.CacheControl;

import java.util.Map;

import retrofit.Response;
import retrofit.Retrofit;
import rx.Observable;
import rx.functions.Func1;

/**
 * Operator that implements the double requests logic. It looks into the response object and decides
 * whether or not it should fire another request. In that case, it concats both the original and the
 * new (double) response observables together.
 */
final class DoubleOperator<T> implements Func1<Response<T>, Observable<Response<T>>> {
  private final Retrofit retrofit;
  private final ObservableRequest<T> observableRequest;
  private final ObservableFactory observableFactory;

  DoubleOperator(Retrofit retrofit, ObservableRequest<T> observableRequest,
      ObservableFactory observableFactory) {
    this.retrofit = retrofit;
    this.observableRequest = observableRequest;
    this.observableFactory = observableFactory;
  }

  @Override
  public Observable<Response<T>> call(Response<T> response) {
    if (observableRequest.airRequest().getMethod() == RequestMethod.GET &&
        response.raw().networkResponse() == null && response.isSuccess()) {
      return Observable.concat(Observable.just(response), newDoubleResponseObservable());
    }
    return Observable.just(response);
  }

  private Observable<Response<T>> newDoubleResponseObservable() {
    // When executing the second request for DOUBLE, we create a new ObservableRequest that
    // changes the original request cache headers to FORCE_NETWORK.
    retrofit.ObservableRequest.Builder builder =
        observableRequest.rawRequest().newBuilder(retrofit);
    // Replace original Cache-Control header with FORCE_NETWORK
    Map<String, String> originalHeaders = observableRequest.rawRequest().headers();
    Map<String, String> headers = new ArrayMap<>(originalHeaders.size() + 1);
    headers.putAll(originalHeaders);
    headers.put("Cache-Control", CacheControl.FORCE_NETWORK.toString());
    ObservableRequest<T> newRequest = new ObservableRequest<>(
        observableRequest.airRequest(), builder.headers(headers).build());

    // Construct a new observable based on the modified request
    return observableFactory.newObservable(newRequest);
  }
}
