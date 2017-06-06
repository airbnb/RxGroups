package com.airbnb.rxgroups;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Creates a tag of the form NonResubscribableTag_className#hashcode.
 */
class NonResubscribingTag {

  private static final String IDENTIFIER = NonResubscribingTag.class.getSimpleName();
  private static final Pattern REGEX_MATCHER = Pattern.compile(IDENTIFIER + "_.*#\\d+");
  private static final String TEMPLATE = IDENTIFIER + "_%s#%d";

  static String create(Object object) {
    return String.format(TEMPLATE, object.getClass().getSimpleName(), object.hashCode());
  }

  static boolean isNonResubscribableTag(String tag) {
    Matcher matcher = REGEX_MATCHER.matcher(tag);
    return matcher.find();
  }

}
