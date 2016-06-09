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

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import rx.Observer;
import rx.observers.TestSubscriber;

import static org.assertj.core.api.Assertions.assertThat;

public class LifecycleResubscriptionTest extends BaseTest {
  private final LifecycleResubscription resubscription = new LifecycleResubscription();
  private final TestSubscriber<ObserverInfo> testSubscriber = new TestSubscriber<>();

  @Test public void simpleString() {
    SimpleString simpleString = new SimpleString();
    resubscription.observers(simpleString).subscribe(testSubscriber);

    testSubscriber.awaitTerminalEvent(1, TimeUnit.SECONDS);
    testSubscriber.assertValueCount(2);
    testSubscriber.assertCompleted();
    testSubscriber.assertNoErrors();

    assertThat(testSubscriber.getOnNextEvents()).containsOnly(
        new ObserverInfo("Object", simpleString.observer1),
        new ObserverInfo("String", simpleString.observer2));
  }

  @Test public void superclass() {
    SubSimpleString subFoo = new SubSimpleString();
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

  @Test public void stringArrayTags() {
    StringArray stringArray = new StringArray();
    resubscription.observers(stringArray).subscribe(testSubscriber);

    testSubscriber.awaitTerminalEvent(1, TimeUnit.SECONDS);
    testSubscriber.assertNoErrors();
    testSubscriber.assertValueCount(2);
    testSubscriber.assertCompleted();

    assertThat(testSubscriber.getOnNextEvents()).containsOnly(
        new ObserverInfo("Class", stringArray.baz),
        new ObserverInfo("Double", stringArray.baz));
  }

  @Test public void integerTag() {
    Int anInt = new Int();
    resubscription.observers(anInt).subscribe(testSubscriber);

    testSubscriber.awaitTerminalEvent(1, TimeUnit.SECONDS);
    testSubscriber.assertNoErrors();
    testSubscriber.assertCompleted();

    assertThat(testSubscriber.getOnNextEvents()).containsOnly(new ObserverInfo("2", anInt.lol));
  }

  @Test public void iterable() {
    ObjectList objectList = new ObjectList();
    resubscription.observers(objectList).subscribe(testSubscriber);

    testSubscriber.awaitTerminalEvent(1, TimeUnit.SECONDS);
    testSubscriber.assertNoErrors();
    testSubscriber.assertCompleted();

    assertThat(testSubscriber.getOnNextEvents()).containsOnly(
        new ObserverInfo("1", objectList.fooBar),
        new ObserverInfo("foo", objectList.fooBar));
  }

  @Test public void array() {
    DoubleArray doubleArray = new DoubleArray();
    resubscription.observers(doubleArray).subscribe(testSubscriber);

    testSubscriber.awaitTerminalEvent(1, TimeUnit.SECONDS);
    testSubscriber.assertNoErrors();
    testSubscriber.assertCompleted();

    assertThat(testSubscriber.getOnNextEvents()).containsOnly(
        new ObserverInfo("1000.0", doubleArray.fooBar),
        new ObserverInfo("2.0", doubleArray.fooBar));
  }

  static class SimpleString {
    public final @AutoResubscribe Observer<String> observer1 = new TestSubscriber<String>() {
      public String resubscriptionTag() {
        return "Object";
      }
    };
    public final @AutoResubscribe Observer<String> observer2 = new TestSubscriber<String>() {
      public String resubscriptionTag() {
        return "String";
      }
    };
  }

  private static class SubSimpleString extends SimpleString {
    public final @AutoResubscribe Observer<String> foo = new TestSubscriber<String>() {
      public String resubscriptionTag() {
        return "Integer";
      }
    };
    public final @AutoResubscribe Observer<String> bar = new TestSubscriber<String>() {
      public String resubscriptionTag() {
        return "Long";
      }
    };
  }

  private static class StringArray {
    public final @AutoResubscribe Observer<String> baz = new TestSubscriber<String>() {
      public String[] resubscriptionTag() {
        return new String[] {"Class", "Double" };
      }
    };
  }

  private static class Int {
    public final @AutoResubscribe Observer<String> lol = new TestSubscriber<String>() {
      public int resubscriptionTag() {
        return 2;
      }
    };
  }

  private static class ObjectList {
    public final @AutoResubscribe Observer<String> fooBar = new TestSubscriber<String>() {
      public List<Object> resubscriptionTag() {
        return Arrays.<Object>asList(1, "foo");
      }
    };
  }

  private static class DoubleArray {
    public final @AutoResubscribe Observer<String> fooBar = new TestSubscriber<String>() {
      public double[] resubscriptionTag() {
        return new double[] { 1000D, 2D };
      }
    };
  }
}