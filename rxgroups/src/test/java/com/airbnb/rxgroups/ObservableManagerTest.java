/*
 * Copyright (C) 2016 Airbnb, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.airbnb.rxgroups;

import org.junit.Test;

import static junit.framework.TestCase.fail;
import static org.assertj.core.api.Assertions.assertThat;

public class ObservableManagerTest {
  private final ObservableManager observableManager = new ObservableManager();

  @Test public void testNewGroup() {
    ObservableGroup group = observableManager.newGroup();
    assertThat(group.id()).isEqualTo(1);
  }

  @Test public void hasNoGroups() {
    assertThat(observableManager.hasGroup(1)).isFalse();
  }

  @Test public void hasGroup() {
    ObservableGroup group = observableManager.newGroup();
    assertThat(observableManager.hasGroup(group.id())).isTrue();
  }

  @Test public void hasNoDestroyedGroups() {
    ObservableGroup group = observableManager.newGroup();
    observableManager.destroy(group);
    assertThat(observableManager.hasGroup(group.id())).isFalse();
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

  @Test public void testGetGroupThrowsIfDestroyed() {
    try {
      ObservableGroup group = observableManager.newGroup();
      group.destroy();
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
