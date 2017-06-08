package test;

import com.airbnb.rxgroups.AutoResubscribe;
import com.airbnb.rxgroups.AutoTag;
import com.airbnb.rxgroups.AutoResubscribingObserver;

public class AutoResubscribingObserver_Pass_All {
  @AutoResubscribe
  AutoResubscribingObserver<Object> observer = new AutoResubscribingObserver<Object>() { };

  @AutoTag
  AutoResubscribingObserver<Object> observer1 = new AutoResubscribingObserver<Object>() { };
}
