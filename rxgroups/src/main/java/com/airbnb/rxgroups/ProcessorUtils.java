package com.airbnb.rxgroups;


import java.util.List;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

class ProcessorUtils {

  static boolean isResubscribingObserver(Element observerFieldElement) {
    return isSubtypeOfType(observerFieldElement.asType(), AutoResubscribingObserver.class
            .getCanonicalName() +  "<?>");
  }


  static boolean isSubtypeOfType(TypeMirror typeMirror, String otherType) {
    if (otherType.equals(typeMirror.toString())) {
      return true;
    }
    if (typeMirror.getKind() != TypeKind.DECLARED) {
      return false;
    }
    DeclaredType declaredType = (DeclaredType) typeMirror;
    List<? extends TypeMirror> typeArguments = declaredType.getTypeArguments();
    if (typeArguments.size() > 0) {
      StringBuilder typeString = new StringBuilder(declaredType.asElement().toString());
      typeString.append('<');
      for (int i = 0; i < typeArguments.size(); i++) {
        if (i > 0) {
          typeString.append(',');
        }
        typeString.append('?');
      }
      typeString.append('>');
      if (typeString.toString().equals(otherType)) {
        return true;
      }
    }
    Element element = declaredType.asElement();
    if (!(element instanceof TypeElement)) {
      return false;
    }
    TypeElement typeElement = (TypeElement) element;
    TypeMirror superType = typeElement.getSuperclass();
    if (isSubtypeOfType(superType, otherType)) {
      return true;
    }
    for (TypeMirror interfaceType : typeElement.getInterfaces()) {
      if (isSubtypeOfType(interfaceType, otherType)) {
        return true;
      }
    }
    return false;
  }
}
