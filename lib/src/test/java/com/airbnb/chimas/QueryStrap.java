package com.airbnb.chimas;

import android.support.annotation.NonNull;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import retrofit.Query;

public class QueryStrap extends TreeSet<Query> {

  public QueryStrap(Comparator<Query> comparator) {
    super(comparator);
  }

  public static QueryStrap make() {
    return new QueryStrap(new Comparator<Query>() {
      @Override public int compare(@NonNull Query lhs, @NonNull Query rhs) {
        return lhs.name().compareTo(rhs.name());
      }
    });
  }

  public QueryStrap kv(String k, long v) {
    return kv(k, Long.toString(v));
  }

  public QueryStrap kv(String k, int v) {
    return kv(k, Integer.toString(v));
  }

  public QueryStrap kv(String k, boolean v) {
    return kv(k, Boolean.toString(v));
  }

  public QueryStrap kv(String k, float v) {
    return kv(k, Float.toString(v));
  }

  public QueryStrap kv(String k, double v) {
    return kv(k, Double.toString(v));
  }

  public QueryStrap kv(String k, String v) {
    add(new Query(k, v));
    return this;
  }

  public QueryStrap mix(List<Query> strap) {
    if (strap != null) {
      addAll(strap);
    }
    return this;
  }

  public QueryStrap mix(Map<String, String> strap) {
    if (strap != null) {
      for(Map.Entry<String, String> entry : strap.entrySet()) {
        kv(entry.getKey(), entry.getValue());
      }
    }
    return this;
  }

  @Override @NonNull public String toString() {
    StringBuilder builder = new StringBuilder("[").append("\n");
    for (Query query : this) {
      builder.append("\t").append(query.toString()).append("\n");
    }
    return builder.append("]").toString();
  }
}
