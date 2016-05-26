/*
 * Copyright (C) 2016 Airbnb, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.airbnb.rxgroups;

import com.airbnb.rxgroups.LifecycleResubscription.ObserverInfo;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

import rx.Observer;
import rx.observers.TestSubscriber;

import static org.assertj.core.api.Assertions.assertThat;

public class LifecycleResubscriptionTest extends BaseTest {
  private final LifecycleResubscription resubscription = new LifecycleResubscription();
  private final TestSubscriber<ObserverInfo> testSubscriber = new TestSubscriber<>();

  @Test public void testObservers() {
    Foo foo = new Foo();
    resubscription.observers(foo).subscribe(testSubscriber);

    testSubscriber.awaitTerminalEvent(1, TimeUnit.SECONDS);
    testSubscriber.assertValueCount(2);
    testSubscriber.assertCompleted();
    testSubscriber.assertNoErrors();

    assertThat(testSubscriber.getOnNextEvents()).containsOnly(
        new ObserverInfo("Object", foo.observer1),
        new ObserverInfo("String", foo.observer2));
  }

  @Test public void testGetFieldsOnSuperClass() {
    SubFoo subFoo = new SubFoo();
    resubscription.observers(subFoo).subscribe(testSubscriber);

    testSubscriber.awaitTerminalEvent(1, TimeUnit.SECONDS);
    testSubscriber.assertCompleted();
    testSubscriber.assertNoErrors();

    assertThat(testSubscriber.getOnNextEvents()).containsOnly(
        new ObserverInfo("Object", subFoo.observer1),
        new ObserverInfo("String", subFoo.observer2),
        new ObserverInfo("Integer", subFoo.foo),
        new ObserverInfo("Long", subFoo.bar));
  }

  @Test public void testMultipleRequestsPerListener() {
    Bar bar = new Bar();
    resubscription.observers(bar).subscribe(testSubscriber);

    testSubscriber.awaitTerminalEvent(1, TimeUnit.SECONDS);
    testSubscriber.assertNoErrors();
    testSubscriber.assertValueCount(2);
    testSubscriber.assertCompleted();

    assertThat(testSubscriber.getOnNextEvents()).containsOnly(
        new ObserverInfo("Class", bar.baz),
        new ObserverInfo("Double", bar.baz));
  }

  @Test public void testTagMethodHasWrongReturnType() {
    Baz baz = new Baz();
    resubscription.observers(baz).subscribe(testSubscriber);

    testSubscriber.awaitTerminalEvent(1, TimeUnit.SECONDS);
    testSubscriber.assertError(RuntimeException.class);
  }

  static class Foo {
    @AutoResubscribe Observer<String> observer1 = new TestSubscriber<String>() {
      String tag() {
        return "Object";
      }
    };
    @AutoResubscribe Observer<String> observer2 = new TestSubscriber<String>() {
      String tag() {
        return "String";
      }
    };
  }

  private static class SubFoo extends Foo {
    @AutoResubscribe Observer<String> foo = new TestSubscriber<String>() {
      String tag() {
        return "Integer";
      }
    };
    @AutoResubscribe Observer<String> bar = new TestSubscriber<String>() {
      String tag() {
        return "Long";
      }
    };
  }

  private static class Bar {
    @AutoResubscribe Observer<String> baz = new TestSubscriber<String>() {
      String[] tag() {
        return new String[] {"Class", "Double" };
      }
    };
  }

  private static class Baz {
    @AutoResubscribe Observer<String> lol = new TestSubscriber<String>() {
      int tag() {
        return 2;
      }
    };
  }
}