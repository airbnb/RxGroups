package test;

import com.airbnb.rxgroups.AutoResubscribe;
import com.airbnb.rxgroups.TaggedObserver;

import io.reactivex.disposables.Disposable;

public class TaggedObserver_Pass_AutoResubscribe {
  @AutoResubscribe
  public TaggedObserver<Object> taggedObserver = new TaggedObserver<Object>() {
    @Override public String getTag() {
      return "stableTag";
    }

    @Override public void onComplete() {

    }

    @Override public void onError(Throwable e) {

    }

    @Override public void onNext(Object o) {

    }

    @Override public void onSubscribe(Disposable d) {

    }
  };
}
