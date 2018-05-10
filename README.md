# RxGroups

[![Build Status](https://travis-ci.org/airbnb/DeepLinkDispatch.svg)](https://travis-ci.org/airbnb/RxGroups)

RxGroups lets you group RxJava `Observable`s together in groups and tie them to your Android lifecycle.
This is especially useful when used with [Retrofit](https://github.com/square/retrofit).

For simple scenarios you can probably just let the original request be cancelled and fire a new one.
However it's easy to see how this becomes a problem in more complex situations.

Let's say your user is submitting a payment. You'll probably want to guarantee that you can reattach
to the same in-flight or completed request after rotating the screen or leaving the Activity and
returning later.

RxGroups will also automatically prevent events from being delivered to your `Activity` or `Fragment`
before `onResume()` and after `onPause()`. If that happens, they will be automatically cached in memory
and delivered once the user returns to your screen. If they never return, the memory is then reclaimed
automatically after `onDestroy()`.

## Usage

1. Add a `GroupLifecycleManager` field to your `Activity`, `Fragment`, `Dialog`, etc. and call its respective lifecycle methods according to your own (eg.: `onPause`, `onResume`, `onDestroy`, etc.);
2. Annotate your `ResubscriptionObserver` with `@AutoResubscribe` and use method `resubscriptionTag()` to tell RxGroups what tag it should use for reattaching your `Observer` to it `Observable` automatically. To use RxGroups with Kotlin don't forget to annotate fields with `@JvmField`. 
3. Before subscribing to your `Observable`, compose it with `observableGroup.transform()` to define a tag for that `Observable`;

### Example

```java
public class MyActivity extends Activity {
  private static final String OBSERVABLE_TAG = "arbitrary_tag";
  private TextView output;
  private FloatingActionButton button;
  private GroupLifecycleManager groupLifecycleManager;
  private ObservableGroup observableGroup;
  private Observable<Long> observable = Observable.interval(1, 1, TimeUnit.SECONDS);

  // The Observer field must be public, otherwise RxGroups can't access it
  @AutoResubscribe public final ResubscriptionObserver<Long> observer = new ResubscriptionObserver<Long>() {
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
    button = (FloatingActionButton) findViewById(R.id.fab);
    SampleApplication application = (SampleApplication) getApplication();
    ObservableManager manager = application.observableManager();
    groupLifecycleManager = GroupLifecycleManager.onCreate(manager, savedInstanceState, this);
    observableGroup = groupLifecycleManager.group();

    button.setOnClickListener(v -> observable
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

**Optional**: If you don't want to use a `ResubscriptionObserver `with `@AutoResubscribe` just use a
regular `Observer` anonymous class with a public method called `resubscriptionTag()`. Eg.:

```java
@AutoResubscribe public final Observer<Long> observer = new Observer<Long>() {
    @Override public void onCompleted() {
    }

    @Override public void onError(Throwable e) {
    }

    @Override public void onNext(Long l) {
    }

    public Object resubscriptionTag() {
      return Arrays.asList("tag1", "tag2", "tag3");
    }
  };
```

If the method doesn't exist or is not `public`, RxGroups will throw a `RuntimeException` letting you know.
You can use any `Object` tag for your `Observer`, including arrays and `List`, In that case, it will
associate the `Observer` with all the tags in the collection, allowing you to share the same
`Observer` with multiple `Observables`.

### Download with Gradle

```groovy
compile 'com.airbnb:rxgroups-android:0.3.5'
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
