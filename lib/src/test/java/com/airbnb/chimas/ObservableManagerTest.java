package com.airbnb.chimas;

import org.junit.Test;

import static junit.framework.TestCase.fail;
import static org.assertj.core.api.Assertions.assertThat;

public class ObservableManagerTest {
  ObservableManager observableManager = new ObservableManager();

  @Test public void testNewGroup() {
    ObservableGroup group = observableManager.newGroup();
    assertThat(group.id()).isEqualTo(1);
  }

  @Test public void testGetGroup() {
    ObservableGroup originalGroup = observableManager.newGroup();
    ObservableGroup group = observableManager.getGroup(originalGroup.id());
    assertThat(group.id()).isEqualTo(1);
  }

  @Test public void testGetGroupThrowsIfNonExistent() {
    try {
      observableManager.getGroup(1);
      fail();
    } catch (IllegalArgumentException ignored) {
    }
  }

  @Test public void testIdsAreNotReused() {
    for (int i = 1; i < 10; i++) {
      ObservableGroup group = observableManager.newGroup();
      assertThat(group.id()).isEqualTo(i);
      group.destroy();
    }
  }

  @Test public void testEquality() {
    ObservableGroup originalGroup = observableManager.newGroup();
    ObservableGroup group1 = observableManager.getGroup(originalGroup.id());
    ObservableGroup group2 = observableManager.getGroup(originalGroup.id());
    assertThat(group1).isEqualTo(group2);
  }

  @Test public void testDifferentGroups() {
    ObservableGroup group1 = observableManager.getGroup(observableManager.newGroup().id());
    ObservableGroup group2 = observableManager.getGroup(observableManager.newGroup().id());
    assertThat(group1).isNotEqualTo(group2);
  }

  @Test public void testGetGroupThrowsAfterDestroyed() {
    ObservableGroup group = observableManager.newGroup();
    observableManager.destroy(group);
    try {
      observableManager.getGroup(group.id());
      fail();
    } catch (IllegalArgumentException ignored) {
    }
  }
}
