package com.airbnb.chimas;

import android.support.annotation.Nullable;
import android.util.Log;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.ResponseBody;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.List;

import okio.Buffer;
import okio.BufferedSource;
import retrofit.Converter;
import retrofit.Response;

public class NetworkException extends Exception {
  private static final String TAG = "NetworkException";
  private final int statusCode;
  private final Object errorResponse;
  private final com.squareup.okhttp.Response rawResponse;
  private final String bodyString;

  public NetworkException(List<Converter.Factory> converterFactories, Type responseType,
      Response response) {
    statusCode = response.code();
    rawResponse = response.raw();
    ResponseBody responseBody = cloneResponseBody(response.errorBody());
    bodyString = getBodyAsString(responseBody);
    errorResponse = getBodyAsErrorResponse(converterFactories, responseType, responseBody);
  }

  public NetworkException(Throwable cause) {
    super(cause);
    statusCode = -1;
    errorResponse = null;
    rawResponse = null;
    bodyString = null;
  }

  public Object errorResponse() {
    return errorResponse;
  }

  /**
   * Checks if this network exception was caused by a server error or by network failure.
   *
   * @return True if we got an error response from the server, false otherwise.
   */
  public boolean hasErrorResponse() {
    return errorResponse != null;
  }

  public com.squareup.okhttp.Response rawResponse() {
    return rawResponse;
  }

  public String bodyString() {
    return bodyString;
  }

  public int statusCode() {
    return statusCode;
  }

  private static String getBodyAsString(@Nullable ResponseBody responseBody) {
    if (responseBody == null) {
      return null;
    }
    try {
      return responseBody.string();
    } catch (IOException e) {
      Log.w(TAG, "Failed to read error ResponseBody as String");
      return null;
    }
  }

  private static ResponseBody cloneResponseBody(@Nullable final ResponseBody body) {
    if (body == null) {
      return null;
    }
    final Buffer buffer = new Buffer();
    try {
      BufferedSource source = body.source();
      buffer.writeAll(source);
      source.close();
    } catch (IOException e) {
      Log.w(TAG, "Failed to clone ResponseBody");
      return null;
    }
    return new ResponseBody() {
      @Override
      public MediaType contentType() {
        return body.contentType();
      }

      @Override
      public long contentLength() {
        return buffer.size();
      }

      @Override
      public BufferedSource source() {
        return buffer.clone();
      }
    };
  }

  private <T> T getBodyAsErrorResponse(List<Converter.Factory> converterFactories,
      Type responseType, @Nullable ResponseBody body) {
    if (body == null) {
      return null;
    }
    try {
      Converter<ResponseBody, ?> converter =
          resolveResponseBodyConverter(converterFactories, responseType);
      //noinspection unchecked
      return (T) converter.convert(body);
    } catch (JsonProcessingException e) {
      Log.e(TAG, "Failed to parse error response as JSON", e);
      return null;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  // Adapted from Retrofit. Copyright Square
  // https://github.com/square/retrofit/blob/master/retrofit/src/main/java/retrofit/Utils.java
  private static Converter<ResponseBody, ?> resolveResponseBodyConverter(
      List<Converter.Factory> converterFactories, Type type) {
    for (int i = 0, count = converterFactories.size(); i < count; i++) {
      Converter<ResponseBody, ?> converter =
          converterFactories.get(i).fromResponseBody(type, new Annotation[0]);
      if (converter != null) {
        return converter;
      }
    }

    StringBuilder builder =
        new StringBuilder("Could not locate ResponseBody converter for ").append(type)
            .append(". Tried:");
    for (Converter.Factory converterFactory : converterFactories) {
      builder.append("\n * ").append(converterFactory.getClass().getName());
    }
    throw new IllegalArgumentException(builder.toString());
  }
}
