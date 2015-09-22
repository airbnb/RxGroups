package com.airbnb.chimas;

/** Groups together an {@link AirRequest} and its respective {@link retrofit.ObservableRequest}. */
final class ObservableRequest<T> {
  private final retrofit.ObservableRequest rawRequest;
  private final AirRequest<T> airRequest;

  ObservableRequest(AirRequest<T> airRequest, retrofit.ObservableRequest rawRequest) {
    this.airRequest = airRequest;
    this.rawRequest = rawRequest;
  }

  retrofit.ObservableRequest rawRequest() {
    return rawRequest;
  }

  AirRequest<T> airRequest() {
    return airRequest;
  }
}
