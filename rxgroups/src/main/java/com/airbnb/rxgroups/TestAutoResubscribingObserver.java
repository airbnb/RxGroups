package com.airbnb.rxgroups;


class TestAutoResubscribingObserver extends AutoResubscribingObserver<String> {
  TestAutoResubscribingObserver(String tag) {
    this.setTag(tag);
  }
}
