package test;

import com.airbnb.rxgroups.AutoResubscribe;
import com.airbnb.rxgroups.AutoResubscribingObserver;

public class AutoResubscribingObserver_Fail_NonStatic {

  class Inner {
    @AutoResubscribe
    private AutoResubscribingObserver<Object> observer = new AutoResubscribingObserver<Object>() { };
  }

}
