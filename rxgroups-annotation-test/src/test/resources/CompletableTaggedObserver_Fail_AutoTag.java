package test;

import com.airbnb.rxgroups.AutoTag;
import com.airbnb.rxgroups.TaggedObserver;
import com.airbnb.rxgroups.CompletableTaggedObserver;

public class CompletableTaggedObserver_Fail_AutoTag {
    @AutoTag
    public CompletableTaggedObserver<Object> taggedObserver = new CompletableTaggedObserver<Object>() {
        @Override
        public String getTag() {
            return "stableTag";
        }

        @Override
        public void onCompleted() {

        }

        @Override
        public void onError(Throwable e) {

        }

        @Override
        public void onNext(Object o) {

        }
    };
}
