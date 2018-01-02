package com.airbnb.rxgroups;

import com.airbnb.rxgroups.processor.ProcessorHelper;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.Nullable;

class ResubscribeHelper {

  private static final Map<Class<?>, Constructor<?>> BINDINGS = new LinkedHashMap<>();

  /**
   * Initializes all helper classes in {@code target} class hierarchy.
   */
  static void initializeAutoTaggingAndResubscription(Object target, ObservableGroup group) {
    Class<?> cls = target.getClass();
    String clsName = cls.getName();
    while (cls != null && !clsName.startsWith("android.") && !clsName.startsWith("java.")) {
      initializeAutoTaggingAndResubscriptionInTargetClassOnly(target, cls, group);
      cls = cls.getSuperclass();
      if (cls != null) {
        clsName = cls.getName();
      }
    }
  }

  static void initializeAutoTaggingAndResubscriptionInTargetClassOnly(Object target,
      Class<?> targetClass,
      ObservableGroup group) {
    Constructor<?> constructor = findConstructorForClass(targetClass);
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

  @Nullable
  private static Constructor<?> findConstructorForClass(Class<?> cls) {
    Constructor<?> bindingCtor = BINDINGS.get(cls);
    if (bindingCtor != null || BINDINGS.containsKey(cls)) {
      return bindingCtor;
    }

    String clsName = cls.getName();
    if (clsName.startsWith("android.") || clsName.startsWith("java.")) {
      BINDINGS.put(cls, bindingCtor);
      return null;
    }
    try {
      Class<?> bindingClass = Class.forName(clsName
          + ProcessorHelper.GENERATED_CLASS_NAME_SUFFIX);
      //noinspection unchecked
      bindingCtor = bindingClass.getConstructor(cls, ObservableGroup.class);
    } catch (ClassNotFoundException e) {
      bindingCtor = findConstructorForClass(cls.getSuperclass());
    } catch (NoSuchMethodException e) {
      throw new RuntimeException("Unable to find binding constructor for " + clsName, e);
    }
    BINDINGS.put(cls, bindingCtor);
    return bindingCtor;
  }
}
