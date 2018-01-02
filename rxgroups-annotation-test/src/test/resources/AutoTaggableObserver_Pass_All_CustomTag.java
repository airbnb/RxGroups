package test;

import com.airbnb.rxgroups.AutoResubscribe;
import com.airbnb.rxgroups.AutoTag;
import com.airbnb.rxgroups.AutoTaggableObserverImpl;

public class AutoTaggableObserver_Pass_All_CustomTag {
  @AutoResubscribe(customTag = "tag1")
  public AutoTaggableObserverImpl<Object> resubscribeObserver = new AutoTaggableObserverImpl<Object>();

  @AutoTag(customTag = "tag2")
  public AutoTaggableObserverImpl<Object> autoTag = new AutoTaggableObserverImpl<Object>();
}
