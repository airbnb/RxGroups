package com.airbnb.chimas;

import retrofit.Response;
import rx.Observable;
import rx.functions.Action1;
import rx.schedulers.Schedulers;

import static com.airbnb.chimas.Util.castOrWrap;

/** Builds an RxJava {@link Observable} object from an Airbnb {@link ObservableRequest} */
final class ObservableFactory {
  private final ResponseMapper responseMapper;

  public ObservableFactory(ResponseMapper responseMapper) {
    this.responseMapper = responseMapper;
  }

  <T> Observable<Response<T>> newObservable(final ObservableRequest<T> observableRequest) {
    //noinspection unchecked
    return observableRequest
        .rawRequest()
        .<Observable<Response<T>>>newCall()
        .observeOn(Schedulers.io())
        .cache()
        .flatMap((ResponseMapper<T>) responseMapper)
        .doOnError(new Action1<Throwable>() {
          @Override
          public void call(Throwable throwable) {
            // Cant simply use AirRequest as the doOnError() argument, since it already
            // implements Func1 (which clashes with Action1 because both have the same
            // erasure).
            observableRequest.airRequest().onError(castOrWrap(throwable));
          }
        })
        .map(observableRequest.airRequest());
  }
}
