package com.airbnb.chimas;

import android.net.Uri;

public abstract class AirFormUrlRequest<T> extends AirRequest<T> {

  public AirFormUrlRequest(Chimas chimas) {
    super(chimas);
  }

  /**
   * AirFormUrlRequest does not include the parameters from getParams() in the URL. They are sent
   * via Form fields instead.
   */
  @Override public String getUrl() {
    Uri.Builder builder = Uri.parse(getBaseUrl() + getPath()).buildUpon();

    addParamsToQuery(builder, getDefaultQueryParams());

    return builder.build().toString();
  }
}
