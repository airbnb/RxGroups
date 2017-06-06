package com.airbnb.rxgroups;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class NonResubscribingTagTest {

  @Test
  public void testMatch() {
    Object test = new Object();
    final String tag = NonResubscribingTag.create(test);
    assertThat(NonResubscribingTag.isNonResubscribableTag(tag)).isTrue();
  }

  @Test
  public void testMatch_innerclass() {
    InnerClass test = new InnerClass();
    final String tag = NonResubscribingTag.create(test);
    assertThat(NonResubscribingTag.isNonResubscribableTag(tag)).isTrue();
  }

  @Test
  public void testMatch_nomatch() {
    String tag = "stableTag";
    assertThat(NonResubscribingTag.isNonResubscribableTag(tag)).isFalse();
  }

  class InnerClass {
  }

}
