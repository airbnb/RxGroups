package com.airbnb.chimas;

public enum RequestMethod {
  GET(0), POST(1), PUT(2), DELETE(3), HEAD(4), OPTIONS(5), TRACE(6), PATCH(7);

  private final int value;

  RequestMethod(int value) {
    this.value = value;
  }

  public int value() {
    return value;
  }
}