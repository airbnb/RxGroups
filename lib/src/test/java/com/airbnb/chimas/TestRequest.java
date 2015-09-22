package com.airbnb.chimas;

import android.text.TextUtils;

import java.util.Map;

public class TestRequest extends AirRequest<String> {
    private long softTtl;
    private long ttl;
    private RequestMethod method = RequestMethod.GET;
    private QueryStrap params;

    public TestRequest(Chimas chimas) {
        super(chimas);
    }

    @Override
    public String getPath() {
        return "/foo/bar";
    }

    @Override public void onError(NetworkException e) {

    }

    @Override public Object getTag() {
        return null;
    }

    @Override
    public long getTTL() {
        return ttl;
    }

    @Override
    public long getSoftTTL() {
        return softTtl;
    }

    @Override
    public RequestMethod getMethod() {
        return method;
    }

    @Override
    public QueryStrap getParams() {
        return params;
    }

    @Override public String getBody() {
        return null;
    }

    @Override public String getContentType() {
        if (!TextUtils.isEmpty(getBody())) {
            return "application/json; charset=UTF-8";
        }
        return "application/x-www-form-urlencoded; charset=UTF-8";
    }

    public TestRequest params(Map<String, String> params) {
        this.params = QueryStrap.make().mix(params);
        return this;
    }

    public TestRequest method(RequestMethod method) {
        this.method = method;
        return this;
    }

    public TestRequest ttl(long ttl) {
        this.ttl = ttl;
        return this;
    }

    public TestRequest softTtl(long softTtl) {
        this.softTtl = softTtl;
        return this;
    }
}
