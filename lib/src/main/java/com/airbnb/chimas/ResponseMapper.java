package com.airbnb.chimas;

import java.lang.reflect.Type;
import java.util.List;

import retrofit.Converter;
import retrofit.Response;
import rx.Observable;
import rx.functions.Func1;

/** Maps a {@link Response} into an error {@link Observable} if it's unsuccessful. */
final class ResponseMapper<T> implements Func1<Response<T>, Observable<Response<T>>> {
  private final List<Converter.Factory> factories;
  private final Type errorResponseType;

  public ResponseMapper(List<Converter.Factory> factories, Type errorResponseType) {
    this.factories = factories;
    this.errorResponseType = errorResponseType;
  }

  @Override
  public Observable<Response<T>> call(Response<T> response) {
    if (response.isSuccess()) {
      return Observable.just(response);
    }
    return Observable.error(new NetworkException(factories, errorResponseType, response));
  }
}
