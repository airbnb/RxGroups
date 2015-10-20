package com.airbnb.chimas;

import org.junit.Test;

import static junit.framework.TestCase.fail;
import static org.assertj.core.api.Assertions.assertThat;

public class RequestManagerTest {
  RequestManager requestManager = new RequestManager();
  @Test public void testNewGroup() {
    ObservableGroup group = requestManager.newGroup();
    assertThat(group.id()).isEqualTo(1);
  }

  @Test public void testGetGroup() {
    ObservableGroup originalGroup = requestManager.newGroup();
    ObservableGroup group = requestManager.getGroup(originalGroup.id());
    assertThat(group.id()).isEqualTo(1);
  }

  @Test public void testGetGroupThrowsIfNonExistent() {
    try {
      requestManager.getGroup(1);
      fail();
    } catch (IllegalArgumentException ignored) {
    }
  }

  @Test public void testIdsAreNotReused() {
    for (int i = 1; i < 10; i++) {
      ObservableGroup group = requestManager.newGroup();
      assertThat(group.id()).isEqualTo(i);
      group.destroy();
    }
  }

  @Test public void testEquality() {
    ObservableGroup originalGroup = requestManager.newGroup();
    ObservableGroup group1 = requestManager.getGroup(originalGroup.id());
    ObservableGroup group2 = requestManager.getGroup(originalGroup.id());
    assertThat(group1).isEqualTo(group2);
  }

  @Test public void testDifferentGroups() {
    ObservableGroup group1 = requestManager.getGroup(requestManager.newGroup().id());
    ObservableGroup group2 = requestManager.getGroup(requestManager.newGroup().id());
    assertThat(group1).isNotEqualTo(group2);
  }

  @Test public void testGetGroupThrowsAfterDestroyed() {
    ObservableGroup group = requestManager.newGroup();
    requestManager.destroy(group);
    try {
      requestManager.getGroup(group.id());
      fail();
    } catch (IllegalArgumentException ignored) {
    }
  }
}
