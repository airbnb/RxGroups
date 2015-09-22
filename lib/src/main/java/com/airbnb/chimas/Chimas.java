package com.airbnb.chimas;

import java.lang.reflect.Type;

import retrofit.Retrofit;

public final class Chimas {
  private final Retrofit retrofit;
  private final ObservableFactory observableFactory;
  private final ObservableRequestFactory observableRequestFactory;

  public Chimas(Type errorResponseType, Retrofit retrofit) {
    this.retrofit = retrofit;
    this.observableFactory = new ObservableFactory(
        new ResponseMapper(retrofit.converterFactories(), errorResponseType));
    this.observableRequestFactory = new ObservableRequestFactory(retrofit);
  }

  ObservableFactory observableFactory() {
    return observableFactory;
  }

  ObservableRequestFactory observableRequestFactory() {
    return observableRequestFactory;
  }

  Retrofit retrofit() {
    return retrofit;
  }
}
