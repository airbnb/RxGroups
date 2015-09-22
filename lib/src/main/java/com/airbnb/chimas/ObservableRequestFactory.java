package com.airbnb.chimas;

import android.net.Uri;
import android.support.v4.util.ArrayMap;

import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.internal.Util;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import retrofit.Field;
import retrofit.Method;
import retrofit.Part;
import retrofit.Query;
import retrofit.Response;
import retrofit.Retrofit;

/** Builds an {@link ObservableRequest} object from an {@link AirRequest} */
final class ObservableRequestFactory {
  private final Retrofit retrofit;

  ObservableRequestFactory(Retrofit retrofit) {
    this.retrofit = retrofit;
  }

  <T> ObservableRequest<T> newObservableRequest(AirRequest<T> airRequest) {
    retrofit.ObservableRequest.Builder builder =
        new retrofit.ObservableRequest.Builder(retrofit)
            .headers(convertHeaders(airRequest.getHeaders()))
            .path(convertPath(airRequest.getUrl()))
            .method(convertMethod(airRequest.getMethod()))
            .responseType(responseType(airRequest.getType()))
            .tag(airRequest.getTag())
            .body(convertBody(airRequest));

    if (airRequest instanceof AirFormUrlRequest) {
      builder.fields(convertFields(airRequest));
    } else if (airRequest instanceof AirMultipartRequest) {
      builder.parts(convertParts((AirMultipartRequest<Object>) airRequest));
    }

    return new ObservableRequest<>(airRequest, builder.build());
  }

  private static Method convertMethod(RequestMethod method) {
    return Method.valueOf(method.name());
  }

  private static Map<String, String> convertHeaders(Map<String, String> headers) {
    Set<Map.Entry<String, String>> entries = headers.entrySet();
    Map<String, String> headersMap = new ArrayMap<>(entries.size());
    for (Map.Entry<String, String> header : entries) {
      headersMap.put(header.getKey(), Util.toHumanReadableAscii(header.getValue()));
    }
    return headersMap;
  }

  private static Type responseType(final Type type) {
    return new ParameterizedType() {
      @Override
      public Type[] getActualTypeArguments() {
        return new Type[] { type };
      }

      @Override
      public Type getOwnerType() {
        return null;
      }

      @Override
      public Type getRawType() {
        return Response.class;
      }
    };
  }

  private static <T> List<Part> convertParts(AirMultipartRequest<T> airRequest) {
    return airRequest.getParts();
  }

  private static String convertPath(String url) {
    Uri uri = Uri.parse(url);
    if (uri.getQuery() != null) {
      return uri.getPath() + "?" + uri.getQuery();
    }
    return uri.getPath();
  }

  private static List<Field> convertFields(AirRequest<?> airRequest) {
    Set<Query> params = airRequest.getParams();
    List<Field> fields = new ArrayList<>(params.size());
    for (Query query : params) {
      fields.add(new Field(query.name(), query.value(), query.encoded()));
    }
    return fields;
  }

  private static Object convertBody(AirRequest airRequest) {
    if (airRequest.getBody() == null) {
      return null;
    }
    MediaType parse = MediaType.parse(airRequest.getContentType());
    return RequestBody.create(parse, airRequest.getBody());
  }
}
