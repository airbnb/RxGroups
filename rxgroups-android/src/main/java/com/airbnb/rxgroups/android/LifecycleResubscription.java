package com.airbnb.rxgroups.android;

import java.lang.reflect.Field;
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
      list.addAll(Arrays.asList(classToCheck.getDeclaredFields()));
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

  private Observable<ObserverInfo> observerInfoList(Object target, Field field) {
    final Observer<?> observer;
    try {
      observer = (Observer<?>) field.get(target);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(
          String.format("Error accessing observer %s. Make sure it's public.", field), e);
    }

    return Observable.from(field.getAnnotation(AutoResubscribe.class).value())
        .map(new Func1<Class<?>, ObserverInfo>() {
          @Override public ObserverInfo call(Class<?> klass) {
            return new ObserverInfo(klass, observer);
          }
        });
  }

  /** Helper class to match an Observer to the Observable type it can be subscribed to. */
  static class ObserverInfo {
    final Class<?> klass;
    final Observer<?> listener;

    ObserverInfo(Class<?> klass, Observer<?> listener) {
      this.klass = klass;
      this.listener = listener;
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
      if (!klass.equals(that.klass)) {
        return false;
      }
      return listener.equals(that.listener);
    }

    @Override
    public int hashCode() {
      int result = klass.hashCode();
      result = 31 * result + listener.hashCode();
      return result;
    }
  }
}
