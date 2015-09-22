package com.airbnb.chimas;

public abstract class SimpleRequestListener<T> extends RequestListener<T> {
  @Override public void onResponse(T response) {
  }

  @Override public void onErrorResponse(NetworkException e) {
  }
}
