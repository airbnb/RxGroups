package com.airbnb.chimas;

import com.squareup.okhttp.HttpUrl;

public abstract class AirFormUrlRequest<T> extends AirRequest<T> {

  public AirFormUrlRequest(Chimas chimas) {
    super(chimas);
  }

  /**
   * AirFormUrlRequest does not include the parameters from getParams() in the URL. They are sent
   * via Form fields instead.
   */
  @Override public String getUrl() {
    HttpUrl.Builder builder = HttpUrl.parse(getBaseUrl() + getPath()).newBuilder();

    addParamsToQuery(builder, getDefaultQueryParams());

    return builder.build().toString();
  }
}
