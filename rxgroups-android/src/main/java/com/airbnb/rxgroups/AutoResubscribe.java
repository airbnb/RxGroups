package com.airbnb.rxgroups;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Used on {@link rx.Observer} fields to indicate that they should be automatically subscribed to a
 * certain Observable, or multiple Observables if they still haven't completed yet.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface AutoResubscribe {
  /** The tag(s) to resubscribe to */
  String[] value();
}
