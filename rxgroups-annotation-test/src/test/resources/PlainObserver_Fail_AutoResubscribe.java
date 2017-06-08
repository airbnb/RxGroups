package test;

import com.airbnb.rxgroups.AutoResubscribe;
import rx.Observer;

public class PlainObserver_Fail_AutoResubscribe {

  @AutoResubscribe
  public Observer<Object> taggedObserver = new Observer<Object>() {

    @Override public void onCompleted() {

    }

    @Override public void onError(Throwable e) {

    }

    @Override public void onNext(Object o) {

    }
  };
}
