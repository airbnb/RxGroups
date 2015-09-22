package com.airbnb.chimas;

final class Util {

  private Util() {
    // no instances
  }

  static NetworkException castOrWrap(Throwable e) {
    if (e instanceof NetworkException) {
      return (NetworkException) e;
    }
    return new NetworkException(e);
  }
}