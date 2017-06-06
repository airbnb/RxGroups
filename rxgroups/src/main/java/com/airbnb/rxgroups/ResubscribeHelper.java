package com.airbnb.rxgroups;

import com.airbnb.rxgroups.processor.ProcessorHelper;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.LinkedHashMap;
import java.util.Map;

class ResubscribeHelper {

  private static final Map<Class<?>, Constructor<?>> BINDINGS = new LinkedHashMap<>();

  static void initializeResubscription(Object target, ObservableGroup group) {
    Constructor<?> constructor = findConstructorForClass(target.getClass(), group);
    if (constructor == null) {
      throw new IllegalArgumentException("Invalid target: " + target);
    }
    invokeConstructor(constructor, target, group);
  }

  static void safeInitializeResubscription(Object target, ObservableGroup group) {
    Constructor<?> constructor = findConstructorForClass(target.getClass(), group);
    if (constructor == null) {
      return;
    }
    invokeConstructor(constructor, target, group);
  }

  private static void invokeConstructor(Constructor<?> constructor, Object target,
                                        ObservableGroup group) {
    try {
      constructor.newInstance(target, group);
    } catch (IllegalAccessException e) {
      throw new RuntimeException("Unable to invoke " + constructor, e);
    } catch (InstantiationException e) {
      throw new RuntimeException("Unable to invoke " + constructor, e);
    } catch (InvocationTargetException e) {
      Throwable cause = e.getCause();
      if (cause instanceof RuntimeException) {
        throw (RuntimeException) cause;
      }
      if (cause instanceof Error) {
        throw (Error) cause;
      }
      throw new RuntimeException("Unable to create resubscribeAll instance.", cause);
    }
  }

  private static Constructor<?> findConstructorForClass(Class<?> cls, ObservableGroup group) {
    Constructor<?> bindingCtor = BINDINGS.get(cls);
    if (bindingCtor != null) {
      return bindingCtor;
    }

    String clsName = cls.getName();
    if (clsName.startsWith("android.") || clsName.startsWith("java.")) {
      return null;
    }

    try {
      Class<?> bindingClass = Class.forName(clsName
          + ProcessorHelper.GENERATED_CLASS_NAME_SUFFIX);
      //noinspection unchecked
      bindingCtor = bindingClass.getConstructor(cls, ObservableGroup.class);
    } catch (ClassNotFoundException e) {
      bindingCtor = findConstructorForClass(cls.getSuperclass(), group);
    } catch (NoSuchMethodException e) {
      throw new RuntimeException("Unable to find binding constructor for " + clsName, e);
    }
    BINDINGS.put(cls, bindingCtor);
    return bindingCtor;
  }
}
