package com.airbnb.chimas;

import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.mockwebserver.MockWebServer;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import java.io.IOException;

import okio.Buffer;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

@Config(sdk = 21, manifest = ChimasTestRunner.MANIFEST_PATH)
@RunWith(ChimasTestRunner.class)
public class AirFormUrlRequestTest {

  @Rule public MockWebServer server = new MockWebServer();

  @Test public void testFormUrlFieldParams() throws Exception {
//    server.enqueue(new MockResponse().setResponseCode(200).setBody("\"YAY\""));
//
//    AirFormUrlRequest<String> request = new AirFormUrlRequest<String>() {
//      @Override public String getPath() {
//        return "foo/bar/baz";
//      }
//
//      @Override public QueryStrap getParams() {
//        return QueryStrap.make().kv("kit", "kat");
//      }
//
//      @Override public RequestMethod getMethod() {
//        return RequestMethod.POST;
//      }
//    };
//
//    Response response = request.toObservable().toBlocking().first().raw();
//    assertThat(response.request().urlString(),
//        equalTo(server.url("/v1/foo/bar/baz").toString() +
//            "?client_id=3092nxybyb0otqw18e8nh5nty&locale=en-US&currency=USD"));
//
//    assertBody(response.request().body(), "kit=kat");
  }

  private static void assertBody(RequestBody body, String expected) {
    assertNotNull(body);
    Buffer buffer = new Buffer();
    try {
      body.writeTo(buffer);
      assertThat(buffer.readUtf8(), equalTo(expected));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}