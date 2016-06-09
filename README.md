# RxGroups

[![Build Status](https://travis-ci.org/airbnb/DeepLinkDispatch.svg)](https://travis-ci.org/airbnb/RxGroups)

RxGroups lets you group RxJava `Observable`s together in groups and tie them to your Android lifecycle.

## Usage

1. Add a `GroupLifecycleManager` field to your `Activity`, `Fragment`, `Dialog`, etc. and call its respective lifecycle methods according to your own (eg.: `onPause`, `onResume`, `onDestroy`, etc.);
2. Annotate your `Observer` with `@AutoResubscribe` and add a method `resubscriptionTag()` to tell RxGroups what tag it should use for reattaching your `Observer` to it `Observable` automatically.
3. Before subscribing to your `Observable`, compose it with `observableGroup.transform()` to tell RxGroups what tag it should associate with that `Observable`;

### Example

```java
public class MyActivity extends Activity {
  private static final String OBSERVABLE_TAG = "arbitrary_tag";
  private TextView output;
  private GroupLifecycleManager groupLifecycleManager;
  private ObservableGroup observableGroup;
  private Observable<Long> observable;

  @AutoResubscribe public final Observer<Long> observer = new Observer<Long>() {
    @Override public void onCompleted() {
      Log.d(TAG, "onCompleted()");
    }

    @Override public void onError(Throwable e) {
      Log.e(TAG, "onError()", e);
    }

    @Override public void onNext(Long l) {
      output.setText(output.getText() + " " + l);
    }

    @Override public Object resubscriptionTag() {
      return OBSERVABLE_TAG;
    }
  };

  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    output = (TextView) findViewById(R.id.txt_output);
    SampleApplication application = (SampleApplication) getApplication();
    ObservableManager manager = application.observableManager();
    groupLifecycleManager = GroupLifecycleManager.onCreate(manager, savedInstanceState, this);
    observableGroup = groupLifecycleManager.group();

    myButton.setOnClickListener(v -> observable
        .compose(observableGroup.<Long>transform(OBSERVABLE_TAG))
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(observer));
  }

  @Override protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    groupLifecycleManager.onSaveInstanceState(outState);
  }

  @Override protected void onResume() {
    super.onResume();
    groupLifecycleManager.onResume();
  }

  @Override protected void onPause() {
    super.onPause();
    groupLifecycleManager.onPause();
  }

  @Override protected void onDestroy() {
    super.onDestroy();
    groupLifecycleManager.onDestroy(this);
  }
}
```

**Bonus**: If you return an Array or a List from your `Observer` `resubscriptionTag()` method, it will associate it with all the tags in the collection, allowing you to share the same `Observer` with multiple `Observables`.


### Download with Gradle

```groovy
compile 'com.airbnb:rxgroups:0.3.0'
```

Check out the [Sample app](https://github.com/airbnb/RxGroups/blob/master/sample/src/main/java/com/airbnb/rxgroups/MainActivity.java) for more details and a complete example!

Snapshots of the development version are available in
[Sonatype's `snapshots` repository](https://oss.sonatype.org/content/repositories/snapshots/).

License
--------

    Copyright 2016 Airbnb, Inc.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.


 [1]: http://airbnb.github.io/airbnb/AirMapView/
