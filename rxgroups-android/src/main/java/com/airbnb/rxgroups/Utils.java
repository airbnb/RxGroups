package com.airbnb.rxgroups;

import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;

import static com.airbnb.rxgroups.ArrayUtils.toObject;

final class Utils {

  static Class<?> getRawType(Type type) {
    if (type == null) throw new NullPointerException("type == null");

    if (type instanceof Class<?>) {
      // Type is a normal class.
      return (Class<?>) type;
    }
    if (type instanceof ParameterizedType) {
      ParameterizedType parameterizedType = (ParameterizedType) type;

      // I'm not exactly sure why getRawType() returns Type instead of Class. Neal isn't either but
      // suspects some pathological case related to nested classes exists.
      Type rawType = parameterizedType.getRawType();
      if (!(rawType instanceof Class)) throw new IllegalArgumentException();
      return (Class<?>) rawType;
    }
    if (type instanceof GenericArrayType) {
      Type componentType = ((GenericArrayType) type).getGenericComponentType();
      return Array.newInstance(getRawType(componentType), 0).getClass();
    }
    if (type instanceof TypeVariable) {
      // We could use the variable's bounds, but that won't work if there are multiple. Having a raw
      // type that's more general than necessary is okay.
      return Object.class;
    }
    if (type instanceof WildcardType) {
      return getRawType(((WildcardType) type).getUpperBounds()[0]);
    }

    throw new IllegalArgumentException("Expected a Class, ParameterizedType, or "
        + "GenericArrayType, but <" + type + "> is of type " + type.getClass().getName());
  }

  static Object[] boxIfPrimitiveArray(Object object) {
    if (object instanceof boolean[]) return toObject((boolean[]) object);
    if (object instanceof byte[]) return toObject((byte[]) object);
    if (object instanceof char[]) return toObject((char[]) object);
    if (object instanceof double[]) return toObject((double[]) object);
    if (object instanceof float[]) return toObject((float[]) object);
    if (object instanceof int[]) return toObject((int[]) object);
    if (object instanceof long[]) return toObject((long[]) object);
    if (object instanceof short[]) return toObject((short[]) object);
    if (object instanceof String[]) return (Object[]) object;
    throw new IllegalArgumentException("Unknown array type for " + object);
  }
}
