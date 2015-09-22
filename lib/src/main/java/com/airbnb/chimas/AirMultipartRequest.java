package com.airbnb.chimas;

import java.util.List;

import retrofit.Part;

public abstract class AirMultipartRequest<T> extends AirRequest<T> {
  public AirMultipartRequest(Chimas chimas) {
    super(chimas);
  }

  @Override public RequestMethod getMethod() {
    return RequestMethod.POST;
  }

  public abstract List<Part> getParts();
}
