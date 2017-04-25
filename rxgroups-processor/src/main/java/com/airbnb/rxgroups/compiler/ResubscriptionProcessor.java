package com.airbnb.rxgroups.compiler;

import com.airbnb.rxgroups.AutoResubscribe;
import com.airbnb.rxgroups.AutoResubscribingObserver;
import com.airbnb.rxgroups.ObservableGroup;
import com.airbnb.rxgroups.processor.ProcessorHelper;
import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import java.io.IOException;
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
    return ImmutableSet.of(AutoResubscribe.class.getCanonicalName());
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
        processObserver(observer, modelClassMap);
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
          info) {
    validateObserverField(observer);
    TypeElement enclosingClass = (TypeElement) observer.getEnclosingElement();
    ClassToGenerateInfo targetClass = getOrCreateTargetClass(info, enclosingClass);

    String observerName = observer.getSimpleName().toString();
    targetClass.addObserver(observerName);
  }

  private void validateObserverField(Element observerFieldElement) {

    TypeElement enclosingClass = (TypeElement) observerFieldElement.getEnclosingElement();

    if (!ProcessorUtils.isResubscribingObserver(observerFieldElement, typeUtils, elementUtils)) {
      logError("%s annotation may only be on %s types. (class: %s, field: %s)",
              AutoResubscribe.class.getSimpleName(), AutoResubscribingObserver.class,
              enclosingClass.getSimpleName(), observerFieldElement.getSimpleName());
    }

    // Verify method modifiers.
    Set<Modifier> modifiers = observerFieldElement.getModifiers();
    if (modifiers.contains(PRIVATE) || modifiers.contains(STATIC)) {
      logError(
              "%s annotations must not be on private or static fields. (class: %s, field: "
                      + "%s)",
              AutoResubscribe.class.getSimpleName(),
              enclosingClass.getSimpleName(), observerFieldElement.getSimpleName());
    }

    // Nested classes must be static
    if (enclosingClass.getNestingKind().isNested()) {
      if (!enclosingClass.getModifiers().contains(STATIC)) {
        logError(
                "Nested classes with %s annotations must be static. (class: %s, field: %s)",
                AutoResubscribe.class.getSimpleName(),
                enclosingClass.getSimpleName(), observerFieldElement.getSimpleName());
      }
    }

    // Verify containing type.
    if (enclosingClass.getKind() != CLASS) {
      logError("%s annotations may only be contained in classes. (class: %s, field: %s)",
              AutoResubscribe.class.getSimpleName(),
              enclosingClass.getSimpleName(), observerFieldElement.getSimpleName());
    }

    // Verify containing class visibility is not private.
    if (enclosingClass.getModifiers().contains(PRIVATE)) {
      logError("%s annotations may not be contained in private classes. (class: %s, "
                      + "field: %s)",
              AutoResubscribe.class.getSimpleName(),
              enclosingClass.getSimpleName(), observerFieldElement.getSimpleName());
    }
  }

  private ClassToGenerateInfo getOrCreateTargetClass(
          Map<TypeElement, ClassToGenerateInfo> modelClassMap, TypeElement classElement) {

    // TODO: (eli_hart 11/26/16) handle super classes
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

    for (String observerName : info.observerNames) {
      String tag = info.originalClassName.getSimpleName().toString() + "_" + observerName;
      builder.addStatement("target.$L.tag = $S", observerName, tag);
      builder.addStatement("group.resubscribe(target.$L)", observerName);
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

  /**
   * Exceptions are caught and logged, and not printed until all processing is done. This allows
   * generated classes to be created first and allows for easier to read compile time error
   * messages.
   */
  private void logError(Exception e) {
    loggedExceptions.add(e);
  }

  private static class ClassToGenerateInfo {
    final List<String> observerNames = new ArrayList<>();
    private final TypeElement originalClassName;
    private final ClassName generatedClassName;

    public ClassToGenerateInfo(TypeElement originalClassName, ClassName generatedClassName) {
      this.originalClassName = originalClassName;
      this.generatedClassName = generatedClassName;
    }

    public void addObserver(String observerName) {
      observerNames.add(observerName);
    }
  }
}
