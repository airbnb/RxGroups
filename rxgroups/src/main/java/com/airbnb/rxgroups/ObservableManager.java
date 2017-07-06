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

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import io.reactivex.Observable;


/**
 * Easily keep reference to {@link Observable}s across lifecycle changes. Observables are grouped by
 * a unique group id, which allows you to manage and reclaim subscriptions made with the same id.
 * Subscribe to observables, and then lock or unlock their observers to control when you get the
 * event back. Events will be held in a queue until an Observer is added and the group is unlocked.
 */
@SuppressWarnings("WeakerAccess")
public class ObservableManager {
  /** Map ids to a group of observables. */
  private final Map<Long, ObservableGroup> observableGroupMap = new ConcurrentHashMap<>();
  private final AtomicLong nextId = new AtomicLong(1);
  private final UUID uuid = UUID.randomUUID();

  /**
   * @return an existing group provided groupId. Throws {@link IllegalStateException} if no group
   * with the provided groupId exists or it is already destroyed.
   */
  public ObservableGroup getGroup(long groupId) {
    ObservableGroup observableGroup = observableGroupMap.get(groupId);

    if (observableGroup == null) {
      throw new IllegalArgumentException("Group not found with groupId=" + groupId);
    }

    if (observableGroup.isDestroyed()) {
      throw new IllegalArgumentException("Group is already destroyed with groupId=" + groupId);
    }

    return observableGroup;
  }

  /** @return a new {@link ObservableGroup} with a unique groupId */
  public ObservableGroup newGroup() {
    long id = nextId.getAndIncrement();
    ObservableGroup observableGroup = new ObservableGroup(id);
    observableGroupMap.put(id, observableGroup);
    return observableGroup;
  }

  UUID id() {
    return uuid;
  }

  /**
   * Clears the provided group. References will be released, and no future results will be returned.
   * Once a group is destroyed it is an error to use it again.
   */
  public void destroy(ObservableGroup group) {
    group.destroy();
    observableGroupMap.remove(group.id());
  }
}
