package com.airbnb.rxgroups.android;

public final class DefaultObservableTagFactory implements ObservableTagFactory {
  public static final DefaultObservableTagFactory INSTANCE = new DefaultObservableTagFactory();

  private DefaultObservableTagFactory() {
  }

  @Override public String tag(Class<?> klass) {
    return klass.toString();
  }
}
