package com.airbnb.rxgroups;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import rx.Observable;

/**
 * Easily keep reference to {@link Observable}s across lifecycle changes. Observables are grouped by
 * a unique group id, which allows you to manage and reclaim subscriptions made with the same id.
 * Subscribe to observables, and then lock or unlock their observers to control when you get the
 * event back. Events will be held in a queue until an Observer is added and the group is unlocked.
 */
public class ObservableManager {
  /** Map ids to a group of observables. */
  private final Map<Long, ObservableGroup> observableGroupMap = new ConcurrentHashMap<>();
  private final AtomicLong nextId = new AtomicLong(1);

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
    long id = nextId.getAndIncrement();
    ObservableGroup observableGroup = new ObservableGroup(id);
    observableGroupMap.put(id, observableGroup);
    return observableGroup;
  }

  /**
   * Clears the provided group. References will be released, and no future results will be returned.
   * Once a group is destroyed it is an error to use it again.
   */
  public void destroy(ObservableGroup group) {
    group.destroy();
    observableGroupMap.remove(group.id());
  }

  @Override public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ObservableManager that = (ObservableManager) o;

    //noinspection SimplifiableIfStatement
    if (nextId.get() != that.nextId.get()) return false;
    return observableGroupMap.equals(that.observableGroupMap);
  }

  @Override public int hashCode() {
    int result = observableGroupMap.hashCode();
    result = 31 * result + (int) (nextId.get() ^ (nextId.get() >>> 32));
    return result;
  }
}
