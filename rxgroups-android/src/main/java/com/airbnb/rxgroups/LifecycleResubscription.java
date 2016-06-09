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

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.annotation.Nullable;

import rx.Observable;
import rx.Observer;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

class LifecycleResubscription {
  /**
   * Returns all {@link ObserverInfo} fields on the target (eg fragment, activity, view) that are
   * marked with {@link AutoResubscribe}. Work is done on a worker thread and the results are
   * delivered on the main thread
   */
  Observable<ObserverInfo> observers(final Object target) {
    return Observable.just(target)
        .observeOn(Schedulers.io())
        .flatMap(new Func1<Object, Observable<Field>>() {
          @Override public Observable<Field> call(Object o) {
            return fields(o);
          }
        })
        .filter(new Func1<Field, Boolean>() {
          @Override public Boolean call(Field field) {
            return field.isAnnotationPresent(AutoResubscribe.class);
          }
        })
        .flatMap(new Func1<Field, Observable<ObserverInfo>>() {
          @Override public Observable<ObserverInfo> call(Field field) {
            return observerInfoList(target, field);
          }
        })
        .observeOn(AndroidSchedulers.mainThread());
  }

  private Observable<Field> fields(Object target) {
    List<Field> list = new ArrayList<>();
    Class<?> classToCheck = target.getClass();

    // Get all fields on the target object, including inherited fields
    while (shouldCheckClass(classToCheck)) {
      list.addAll(Arrays.asList(classToCheck.getFields()));
      classToCheck = classToCheck.getSuperclass();
    }

    return Observable.from(list);
  }

  private boolean shouldCheckClass(@Nullable Class<?> clazz) {
    if (clazz == null) {
      return false;
    }

    String qualifiedName = clazz.getName();
    return !qualifiedName.startsWith("android.") && !qualifiedName.startsWith("java.");
  }

  private Observable<ObserverInfo> observerInfoList(final Object target, Field field) {
    final Observer<?> observer;
    try {
      observer = (Observer<?>) field.get(target);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(
          String.format("Error accessing observer %s. Make sure it's public.", field), e);
    }

    return Observable.just(field).flatMap(new Func1<Field, Observable<ObserverInfo>>() {
      @Override public Observable<ObserverInfo> call(Field field) {
        Object tag;
        //noinspection TryWithIdenticalCatches
        try {
          Class<?> fieldClass = observer.getClass();
          Method tagMethod = fieldClass.getMethod("resubscriptionTag");
          tag = tagMethod.invoke(observer);
        } catch (NoSuchMethodException e) {
          throw new RuntimeException("Please define a method named 'resubscriptionTag()'", e);
        } catch (InvocationTargetException e) {
          throw new RuntimeException("Exception thrown from 'resubscriptionTag()'", e);
        } catch (IllegalAccessException e) {
          throw new RuntimeException(
              "Method 'resubscriptionTag()' is not accessible. Make sure it's public.", e);
        }

        Class<?> rawParameterType = Utils.getRawType(tag.getClass());
        Func1<Object, ObserverInfo> collectionMapper = new Func1<Object, ObserverInfo>() {
          @Override public ObserverInfo call(Object s) {
            return new ObserverInfo(s.toString(), observer);
          }
        };
        //noinspection ConstantConditions
        if (Iterable.class.isAssignableFrom(rawParameterType)) {
          return Observable.from((Iterable<?>) tag).map(collectionMapper);
        }
        if (rawParameterType.isArray()) {
          return Observable.from(Utils.boxIfPrimitiveArray(tag)).map(collectionMapper);
        }
        return Observable.just(new ObserverInfo(tag.toString(), observer));
      }
    });
  }

  /** Helper class to match an Observer to the Observable type it can be subscribed to. */
  static class ObserverInfo {
    final String tag;
    final Observer<?> observer;

    ObserverInfo(String tag, Observer<?> observer) {
      this.tag = tag;
      this.observer = observer;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      ObserverInfo that = (ObserverInfo) o;

      //noinspection SimplifiableIfStatement
      if (!tag.equals(that.tag)) {
        return false;
      }
      return observer.equals(that.observer);
    }

    @Override
    public int hashCode() {
      int result = tag.hashCode();
      result = 31 * result + observer.hashCode();
      return result;
    }

    @Override public String toString() {
      return "ObserverInfo{" +
          "tag='" + tag + '\'' +
          ", observer=" + observer +
          '}';
    }
  }
}
