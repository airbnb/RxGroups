package test;

import com.airbnb.rxgroups.AutoResubscribe;
import com.airbnb.rxgroups.AutoTag;
import com.airbnb.rxgroups.AutoTaggableObserverImpl;

public class AutoTaggableObserver_Pass_All {
  @AutoResubscribe
  public AutoTaggableObserverImpl<Object> resubscribeObserver = new AutoTaggableObserverImpl<Object>();

  @AutoTag
  public AutoTaggableObserverImpl<Object> autoTag = new AutoTaggableObserverImpl<Object>();
}
