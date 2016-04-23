package com.airbnb.rxgroups.android;

public final class DefaultObservableTagFactory implements ObservableTagFactory {
  @Override public String tag(Class<?> klass) {
    return klass.toString();
  }
}
