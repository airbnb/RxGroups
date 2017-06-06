package com.airbnb.rxgroups;

/**
 * Used on {@link AutoTaggableObserver} fields to indicate that a unique tag should automatically be
 * injected into the Observer. Unlike {@link AutoResubscribe} this annotation does _not_
 * signify that the Observer should be resubscribed upon initialization.
 */
public @interface AutoTag {
  /**
   * @return A custom tag to use instead of auto-generated tag.
   * This tag should be unique within the RxGroup.
   */
  String customTag() default "";
}
