package test;

import com.airbnb.rxgroups.AutoTag;
import com.airbnb.rxgroups.AutoResubscribingObserver;

public class AutoResubscribingObserver_Fail_Private_AutoTag {
  @AutoTag
  private AutoResubscribingObserver<Object> observer = new AutoResubscribingObserver<Object>() { };
}