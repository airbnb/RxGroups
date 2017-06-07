package com.airbnb.rxgroups;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;


public class ResubscribeHelperTest {

  static class BaseClass {
    boolean baseInitialized;
  }

  static class MiddleClass extends BaseClass {
    boolean middleInitialized;
  }

  private static class LeafClass extends MiddleClass {
    boolean leafInitialized;
  }

  @Test
  public void leafClassInitDoesIntializeEntireHierarchy() {
    LeafClass leafClass = new LeafClass();
    ResubscribeHelper.initializeAutoTaggingAndResubscription(leafClass, new ObservableGroup(1));
    assertThat(leafClass.baseInitialized).isTrue();
    assertThat(leafClass.middleInitialized).isTrue();
    assertThat(leafClass.leafInitialized).isTrue();
  }

  @Test
  public void leafClassOnlyInitDoesNotInitializeEntireHierarchy() {
    LeafClass leafClass = new LeafClass();
    ResubscribeHelper.initializeAutoTaggingAndResubscriptionInTargetClassOnly(leafClass,
        LeafClass.class,
        new ObservableGroup(1));
    assertThat(leafClass.baseInitialized).isFalse();
    assertThat(leafClass.middleInitialized).isFalse();
    assertThat(leafClass.leafInitialized).isTrue();
  }

  @SuppressWarnings("unused")
  public static class BaseClass_ObservableResubscriber {
    boolean initialized = false;

    public BaseClass_ObservableResubscriber(BaseClass target, ObservableGroup group) {
      target.baseInitialized = true;
    }
  }

  @SuppressWarnings("unused")
  public static class MiddleClass_ObservableResubscriber {
    boolean initialized = false;

    public MiddleClass_ObservableResubscriber(MiddleClass target, ObservableGroup group) {
      target.middleInitialized = true;
    }
  }

  @SuppressWarnings("unused")
  public static class LeafClass_ObservableResubscriber {
    boolean initialized = false;

    public LeafClass_ObservableResubscriber(LeafClass target, ObservableGroup group) {
      target.leafInitialized = true;
    }
  }
}
