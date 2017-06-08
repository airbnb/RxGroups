package test;

import com.airbnb.rxgroups.AutoTag;
import com.airbnb.rxgroups.TaggedObserver;

public class TaggedObserver_Fail_AutoTag {
    @AutoTag
    public TaggedObserver<Object> taggedObserver = new TaggedObserver<Object>() {
        @Override public String getTag() {
            return "stableTag";
        }

        @Override public void onCompleted() {

        }

        @Override public void onError(Throwable e) {

        }

        @Override public void onNext(Object o) {

        }
    };
}
