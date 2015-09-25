package com.airbnb.chimas;

import com.google.common.reflect.TypeToken;
import com.squareup.okhttp.HttpUrl;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import retrofit.Query;
import retrofit.Response;
import retrofit.Retrofit;
import rx.Observable;
import rx.functions.Func1;

public abstract class AirRequest<T> implements Func1<Response<T>, Response<T>> {
  // @formatter:off
  private final Type type = new TypeToken<T>(getClass()) { }.getType();
  // @formatter:on
  private final Retrofit retrofit;
  private final ObservableRequestFactory observableRequestFactory;
  private final ObservableFactory observableFactory;

  public AirRequest(Chimas chimas) {
    retrofit = chimas.retrofit();
    observableRequestFactory = chimas.observableRequestFactory();
    observableFactory = chimas.observableFactory();
  }

  public Observable<Response<T>> toObservable() {
    ObservableRequest<T> observableRequest = observableRequestFactory.newObservableRequest(this);
    return observableFactory.newObservable(observableRequest)
        .flatMap(new DoubleOperator<>(retrofit, observableRequest, observableFactory));
  }

  /** Returns this request's tag. */
  public abstract Object getTag();

  public RequestMethod getMethod() {
    return RequestMethod.GET;
  }

  @Override
  public Response<T> call(Response<T> response) {
    return response;
  }

  public Map<String, String> getHeaders() {
    return Collections.emptyMap();
  }

  /** @return a map of request parameters. */
  public abstract Set<Query> getParams();

  /** @return the POST/PUT String body. */
  public abstract String getBody();

  public abstract String getContentType();

  /** @return this request's path */
  public String getPath() {
    return "";
  }

  /** @return this request's absolute base url */
  protected abstract String getBaseUrl();

  public List<Query> getDefaultQueryParams() {
    return Collections.emptyList();
  }

  public abstract void onError(NetworkException e);

  /**
   * @return the absolute URL for this request, eg.: https://api.airbnb.com/api/v1/users/1234?client_id=9875&username=johndoe&locale=en-US
   */
  public String getUrl() {
    List<Query> queryParams = new ArrayList<>(getDefaultQueryParams());
    if (getParams() != null) {
      queryParams.addAll(getParams());
    }

    HttpUrl.Builder builder = HttpUrl.parse(getBaseUrl() + getPath()).newBuilder();

    addParamsToQuery(builder, queryParams);

    return builder.build().toString();
  }

  public Type getType() {
    return type;
  }

  protected static void addParamsToQuery(HttpUrl.Builder builder, List<Query> queryParams) {
    String encoding = "UTF-8";
    for (Query entry : queryParams) {
      String queryName = entry.name();
      String queryValue = entry.value();
      if (queryValue == null) {
        continue;
      }
      try {
        String name;
        String value;
        if (!entry.encoded()) {
          name = URLEncoder.encode(queryName, encoding);
          value = URLEncoder.encode(queryValue, encoding);
        } else {
          name = queryName;
          value = queryValue;
        }
        builder.addEncodedQueryParameter(name, value);
      } catch (UnsupportedEncodingException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
