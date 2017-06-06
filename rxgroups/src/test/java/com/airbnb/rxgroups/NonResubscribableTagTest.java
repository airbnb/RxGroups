package com.airbnb.rxgroups;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class NonResubscribableTagTest {

  @Test
  public void testMatch() {
    Object test = new Object();
    final String tag = NonResubscribableTag.create(test);
    assertThat(NonResubscribableTag.isNonResubscribableTag(tag)).isTrue();
  }

  @Test
  public void testMatch_innerclass() {
    InnerClass test = new InnerClass();
    final String tag = NonResubscribableTag.create(test);
    assertThat(NonResubscribableTag.isNonResubscribableTag(tag)).isTrue();
  }

  @Test
  public void testMatch_nomatch() {
    String tag = "stableTag";
    assertThat(NonResubscribableTag.isNonResubscribableTag(tag)).isFalse();
  }

  class InnerClass {
  }

}
