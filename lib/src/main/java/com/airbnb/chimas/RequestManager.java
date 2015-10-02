package com.airbnb.chimas;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import rx.Observable;

/**
 * Easily keep reference to {@link Observable}s across lifecycle changes. Observables are grouped by
 * a unique group id, which allows you to manage and reclaim subscriptions made with the same id.
 * Subscribe to observables, and then lock or unlock their observers to control when you get the
 * event back. Events will be held in a queue until an Observer is added and the group is unlocked.
 */
public final class RequestManager {
  /** Map ids to a group of observables. */
  private final Map<Long, ObservableGroup> observableGroupMap = new ConcurrentHashMap<>();

  /** @return an existing group or a new group with the provided groupId */
  public ObservableGroup getGroup(long groupId) {
    ObservableGroup observableGroup = observableGroupMap.get(groupId);

    if (observableGroup == null) {
      throw new IllegalArgumentException("Group not found with groupId=" + groupId);
    }

    return observableGroup;
  }


  /** @return a new {@link ObservableGroup} with a unique groupId */
  public ObservableGroup newGroup() {
    long id;
    if (!observableGroupMap.isEmpty()) {
      id = Collections.max(observableGroupMap.keySet()) + 1;
    } else {
      id = 1;
    }
    ObservableGroup observableGroup = new ObservableGroup(id);
    observableGroupMap.put(id, observableGroup);
    return observableGroup;
  }

  /**
   * Clears the provided group. References will be released, and no future results will be returned.
   * Also clears up the groupId so it could be reused in the future by a different ObservableGroup.
   */
  public void cancel(ObservableGroup group) {
    group.cancel();
    observableGroupMap.remove(group.id());
  }
}
