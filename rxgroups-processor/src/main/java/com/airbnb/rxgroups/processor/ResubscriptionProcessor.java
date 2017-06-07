package com.airbnb.rxgroups.processor;

import com.airbnb.rxgroups.AutoResubscribe;
import com.airbnb.rxgroups.AutoResubscribingObserver;
import com.airbnb.rxgroups.AutoTag;
import com.airbnb.rxgroups.AutoTaggableObserver;
import com.airbnb.rxgroups.BaseObservableResubscriber;
import com.airbnb.rxgroups.ObservableGroup;
import com.airbnb.rxgroups.TaggedObserver;
import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

import static com.airbnb.rxgroups.processor.ResubscriptionProcessor.ObserverType.AUTO_RESUBSCRIBE_OBSERVER;
import static com.airbnb.rxgroups.processor.ResubscriptionProcessor.ObserverType.TAGGED_OBSERVER;
import static javax.lang.model.element.ElementKind.CLASS;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

@AutoService(Processor.class)
public class ResubscriptionProcessor extends AbstractProcessor {
  private Filer filer;
  private Messager messager;
  private Elements elementUtils;
  private Types typeUtils;
  private final List<Exception> loggedExceptions = new ArrayList<>();

  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);
    filer = processingEnv.getFiler();
    messager = processingEnv.getMessager();
    elementUtils = processingEnv.getElementUtils();
    typeUtils = processingEnv.getTypeUtils();
  }

  @Override
  public Set<String> getSupportedAnnotationTypes() {
    return ImmutableSet.of(AutoResubscribe.class.getCanonicalName(),
        AutoTag.class.getCanonicalName());
  }

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    LinkedHashMap<TypeElement, ClassToGenerateInfo> modelClassMap = new LinkedHashMap<>();
    for (Element observer : roundEnv.getElementsAnnotatedWith(AutoResubscribe.class)) {
      try {
        processObserver(observer, modelClassMap, AutoResubscribe.class);
      } catch (Exception e) {
        logError(e);
      }
    }

    for (Element observer : roundEnv.getElementsAnnotatedWith(AutoTag.class)) {
      try {
        processObserver(observer, modelClassMap, AutoTag.class);
      } catch (Exception e) {
        logError(e);
      }
    }

    for (Map.Entry<TypeElement, ClassToGenerateInfo> modelEntry : modelClassMap.entrySet()) {
      try {
        generateClass(modelEntry.getValue());
      } catch (Exception e) {
        logError(e);
      }
    }

    if (roundEnv.processingOver()) {
      for (Exception loggedException : loggedExceptions) {
        messager.printMessage(Diagnostic.Kind.ERROR, loggedException.toString());
      }
    }

    // Let other processors access our annotations as well
    return false;
  }

  private void processObserver(Element observer, LinkedHashMap<TypeElement, ClassToGenerateInfo>
          info, Class<? extends Annotation> annotationClass) {
    validateObserverField(observer, annotationClass);
    TypeElement enclosingClass = (TypeElement) observer.getEnclosingElement();
    ClassToGenerateInfo targetClass = getOrCreateTargetClass(info, enclosingClass);

    String observerName = observer.getSimpleName().toString();

    boolean isAutoResubscribing =
        ProcessorUtils.isResubscribingObserver(observer, typeUtils, elementUtils);
    ObserverType observerType = isAutoResubscribing ? AUTO_RESUBSCRIBE_OBSERVER : TAGGED_OBSERVER;
    boolean isAutoTaggable = ProcessorUtils.isAutoTaggable(observer, typeUtils, elementUtils);
    boolean shouldAutoResubscribe = annotationClass == AutoResubscribe.class;
    String customTag = getCustomTag(observer, annotationClass);

    targetClass.addObserver(observerName, new ObserverInfo(observerType, customTag, isAutoTaggable,
        shouldAutoResubscribe));
  }

  private String getCustomTag(Element observer, Class<? extends Annotation> annotationClass) {
    String customTag = "";
    if (annotationClass == AutoResubscribe.class) {
      customTag = ((AutoResubscribe)observer.getAnnotation(annotationClass)).customTag();
    }
    else if (annotationClass == AutoTag.class) {
      customTag = ((AutoTag)observer.getAnnotation(annotationClass)).customTag();
    }
    return customTag;
  }

  private void validateObserverField(Element observerFieldElement,
                                     Class<? extends Annotation> annotationClass) {

    TypeElement enclosingClass = (TypeElement) observerFieldElement.getEnclosingElement();

    if (annotationClass == AutoResubscribe.class
        && !ProcessorUtils.isTaggedObserver(observerFieldElement, typeUtils, elementUtils)) {
      logError("%s annotation may only be on %s types. (class: %s, field: %s)",
              annotationClass.getSimpleName(), TaggedObserver.class,
              enclosingClass.getSimpleName(), observerFieldElement.getSimpleName());
    }

    if (annotationClass == AutoTag.class &&
        !(ProcessorUtils.isAutoTaggable(observerFieldElement, typeUtils, elementUtils) ||
         ProcessorUtils.isResubscribingObserver(observerFieldElement, typeUtils, elementUtils))) {
      logError("%s annotation may only be on %s or %s types. (class: %s, field: %s)",
          annotationClass.getSimpleName(), AutoTaggableObserver.class, AutoResubscribingObserver.class,
          enclosingClass.getSimpleName(), observerFieldElement.getSimpleName());
    }

    // Verify method modifiers.
    Set<Modifier> modifiers = observerFieldElement.getModifiers();
    if (modifiers.contains(PRIVATE) || modifiers.contains(STATIC)) {
      logError(
              "%s annotations must not be on private or static fields. (class: %s, field: "
                      + "%s)",
              annotationClass.getSimpleName(),
              enclosingClass.getSimpleName(), observerFieldElement.getSimpleName());
    }

    // Nested classes must be static
    if (enclosingClass.getNestingKind().isNested()) {
      if (!enclosingClass.getModifiers().contains(STATIC)) {
        logError(
                "Nested classes with %s annotations must be static. (class: %s, field: %s)",
                annotationClass.getSimpleName(),
                enclosingClass.getSimpleName(), observerFieldElement.getSimpleName());
      }
    }

    // Verify containing type.
    if (enclosingClass.getKind() != CLASS) {
      logError("%s annotations may only be contained in classes. (class: %s, field: %s)",
              annotationClass.getSimpleName(),
              enclosingClass.getSimpleName(), observerFieldElement.getSimpleName());
    }

    // Verify containing class visibility is not private.
    if (enclosingClass.getModifiers().contains(PRIVATE)) {
      logError("%s annotations may not be contained in private classes. (class: %s, "
                      + "field: %s)",
              annotationClass.getSimpleName(),
              enclosingClass.getSimpleName(), observerFieldElement.getSimpleName());
    }
  }

  private ClassToGenerateInfo getOrCreateTargetClass(
          Map<TypeElement, ClassToGenerateInfo> modelClassMap, TypeElement classElement) {

    ClassToGenerateInfo classToGenerateInfo = modelClassMap.get(classElement);

    if (classToGenerateInfo == null) {
      ClassName generatedClassName = getGeneratedClassName(classElement);
      classToGenerateInfo = new ClassToGenerateInfo(classElement, generatedClassName);
      modelClassMap.put(classElement, classToGenerateInfo);
    }

    return classToGenerateInfo;
  }

  private ClassName getGeneratedClassName(TypeElement classElement) {
    String packageName = elementUtils.getPackageOf(classElement).getQualifiedName().toString();

    int packageLen = packageName.length() + 1;
    String className =
            classElement.getQualifiedName().toString().substring(packageLen).replace('.', '$');

    return ClassName.get(packageName, className + ProcessorHelper.GENERATED_CLASS_NAME_SUFFIX);
  }

  private void generateClass(ClassToGenerateInfo info) throws IOException {
    TypeSpec generatedClass = TypeSpec.classBuilder(info.generatedClassName)
            .superclass(BaseObservableResubscriber.class)
            .addJavadoc("Generated file. Do not modify!")
            .addModifiers(Modifier.PUBLIC)
            .addMethod(generateConstructor(info))
            .build();

    JavaFile.builder(info.generatedClassName.packageName(), generatedClass)
            .build()
            .writeTo(filer);
  }

  private MethodSpec generateConstructor(ClassToGenerateInfo info) {
    MethodSpec.Builder builder = MethodSpec.constructorBuilder()
            .addModifiers(PUBLIC)
            .addParameter(ParameterSpec.builder(TypeName.get(info.originalClassName.asType())
                    , "target").build())
            .addParameter(ParameterSpec.builder(TypeName.get(ObservableGroup.class), "group")
                    .build());

    for (Map.Entry<String, ObserverInfo> observerNameAndType
        : info.observerNamesToType.entrySet()) {
      String observerName = observerNameAndType.getKey();
      ObserverInfo observerInfo = observerNameAndType.getValue();
      if (observerInfo.type == AUTO_RESUBSCRIBE_OBSERVER || observerInfo.autoTaggable) {
        String tag = "".equals(observerInfo.customTag) ?
            info.originalClassName.getSimpleName().toString() + "_" + observerName
            : observerInfo.customTag;
        builder.addStatement("setTag(target.$L, $S)", observerName, tag);
      }
      if (observerInfo.shouldAutoResubscribe) {
        builder.addStatement("group.resubscribeAll(target.$L)", observerName);
      }
    }

    return builder.build();
  }

  private static class RxGroupsResubscriptionProcessorException extends Exception {
    RxGroupsResubscriptionProcessorException(String message) {
      super(message);
    }
  }

  private void logError(String msg, Object... args) {
    logError(new RxGroupsResubscriptionProcessorException(String.format(msg, args)));
  }

  enum ObserverType {
    TAGGED_OBSERVER,
    AUTO_RESUBSCRIBE_OBSERVER
  }

  private static class ObserverInfo {
    final ObserverType type;
    final String customTag;
    final boolean autoTaggable;
    final boolean shouldAutoResubscribe;

    ObserverInfo(ObserverType type, String customTag, boolean autoTaggable,
                 boolean shouldAutoResubscribe) {
      this.type = type;
      this.customTag = customTag;
      this.autoTaggable = autoTaggable;
      this.shouldAutoResubscribe = shouldAutoResubscribe;
    }
  }

  /**
   * Exceptions are caught and logged, and not printed until all processing is done. This allows
   * generated classes to be created first and allows for easier to read compile time error
   * messages.
   */
  private void logError(Exception e) {
    loggedExceptions.add(e);
  }

  private static class ClassToGenerateInfo {
    final Map<String, ObserverInfo> observerNamesToType = new LinkedHashMap<>();
    private final TypeElement originalClassName;
    private final ClassName generatedClassName;

    public ClassToGenerateInfo(TypeElement originalClassName, ClassName generatedClassName) {
      this.originalClassName = originalClassName;
      this.generatedClassName = generatedClassName;
    }

    public void addObserver(String observerName, ObserverInfo type) {
      observerNamesToType.put(observerName, type);
    }
  }
}
