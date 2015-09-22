package com.airbnb.chimas;

import rx.functions.Action0;

/** An action that does nothing */
final class DummyAction implements Action0 {
  private static final DummyAction INSTANCE = new DummyAction();

  private DummyAction() {
  }

  static DummyAction instance() {
    return INSTANCE;
  }

  @Override
  public void call() {
  }
}
