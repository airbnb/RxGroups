package com.airbnb.rxgroups.processor;


import com.airbnb.rxgroups.AutoResubscribingObserver;
import com.airbnb.rxgroups.AutoTaggableObserver;
import com.airbnb.rxgroups.TaggedObserver;

import javax.lang.model.element.Element;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

class ProcessorUtils {

  static boolean isResubscribingObserver(Element observerFieldElement, Types typeUtil, Elements
          elementUtil) {
    final TypeMirror autoResubscribingTypeMirror = elementUtil.getTypeElement(
            AutoResubscribingObserver.class.getCanonicalName()).asType();
    return typeUtil.isAssignable(observerFieldElement.asType(), typeUtil.erasure(
            autoResubscribingTypeMirror));
  }

  static boolean isTaggedObserver(Element observerFieldElement, Types typeUtil, Elements
      elementUtil) {
    final TypeMirror autoResubscribingTypeMirror = elementUtil.getTypeElement(
        TaggedObserver.class.getCanonicalName()).asType();
    return typeUtil.isAssignable(observerFieldElement.asType(), typeUtil.erasure(
        autoResubscribingTypeMirror));
  }

  static boolean isAutoTaggable(Element observerFieldElement, Types typeUtil, Elements
      elementUtil) {
    final TypeMirror autoResubscribingTypeMirror = elementUtil.getTypeElement(
        AutoTaggableObserver.class.getCanonicalName()).asType();
    return typeUtil.isAssignable(observerFieldElement.asType(), typeUtil.erasure(
        autoResubscribingTypeMirror));
  }
}
