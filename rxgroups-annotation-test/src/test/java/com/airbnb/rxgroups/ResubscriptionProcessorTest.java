package com.airbnb.rxgroups;

import com.airbnb.rxgroups.processor.ResubscriptionProcessor;
import com.google.common.truth.Truth;
import com.google.testing.compile.JavaFileObjects;
import com.google.testing.compile.JavaSourceSubjectFactory;

import org.junit.Test;

import javax.tools.JavaFileObject;

public class ResubscriptionProcessorTest {

  @Test public void autoResubscribeObserver_worksWithAll() throws Exception {
    JavaFileObject source = JavaFileObjects.forResource("AutoResubscribingObserver_Pass_All.java");

    JavaFileObject resubscriberSource = JavaFileObjects.forSourceString("test.AutoResubscribingObserver_Pass_All", ""
        + "package test;\n"
        + "import com.airbnb.rxgroups.BaseObservableResubscriber;\n"
        + "import com.airbnb.rxgroups.ObservableGroup;\n"
        + "public class AutoResubscribingObserver_Pass_All_ObservableResubscriber extends BaseObservableResubscriber {\n"
        + "  public AutoResubscribingObserver_Pass_All_ObservableResubscriber(AutoResubscribingObserver_Pass_All target, ObservableGroup group) {\n"
        + "     setTag(target.observer, \"AutoResubscribingObserver_Pass_All_observer\");\n"
        + "     group.resubscribeAll(target.observer);\n"
        + "     setTag(target.observer1, \"AutoResubscribingObserver_Pass_All_observer1\");\n"
        + "  }\n"
        + "}\n"
        + ""
    );

    Truth.assertAbout(JavaSourceSubjectFactory.javaSource()).that(source)
        .withCompilerOptions("-Xlint:-processing")
        .processedWith(new ResubscriptionProcessor())
        .compilesWithoutWarnings()
        .and()
        .generatesSources(resubscriberSource);
  }

  @Test public void taggedObserver_worksWithAutoResubscribe() throws Exception {
    JavaFileObject source = JavaFileObjects.forResource("TaggedObserver_Pass_AutoResubscribe.java");

    JavaFileObject resubscriberSource = JavaFileObjects.forSourceString("test.TaggedObserver_Pass_AutoResubscribe_ObservableResubscriber", ""
        + "package test;\n"
        + "import com.airbnb.rxgroups.BaseObservableResubscriber;\n"
        + "import com.airbnb.rxgroups.ObservableGroup;\n"
        + "public class TaggedObserver_Pass_AutoResubscribe_ObservableResubscriber extends BaseObservableResubscriber {\n"
        + "  public TaggedObserver_ObservableResubscriber(TaggedObserver_Pass_AutoResubscribe target, ObservableGroup group) {\n"
        + "     group.resubscribeAll(target.taggedObserver);\n"
        + "  }\n"
        + "}\n"
        + ""
    );

    Truth.assertAbout(JavaSourceSubjectFactory.javaSource()).that(source)
        .withCompilerOptions("-Xlint:-processing")
        .processedWith(new ResubscriptionProcessor())
        .compilesWithoutWarnings()
        .and()
        .generatesSources(resubscriberSource);
  }

  @Test public void autoTaggableObserver_worksWithAll() throws Exception {
    JavaFileObject source = JavaFileObjects.forResource("AutoTaggableObserver_Pass_All.java");

    JavaFileObject resubscriberSource = JavaFileObjects.forSourceString("test.AutoTaggableObserver_Pass_All", ""
        + "package test;\n"
        + "import com.airbnb.rxgroups.BaseObservableResubscriber;\n"
        + "import com.airbnb.rxgroups.ObservableGroup;\n"
        + "\n"
        + "public class AutoTaggableObserver_Pass_All_ObservableResubscriber extends BaseObservableResubscriber {\n"
        + "  public AutoTaggableObserver_Pass_All_ObservableResubscriber(AutoTaggableObserver_Pass_All target, ObservableGroup group) {\n"
        + "     setTag(target.resubscribeObserver, \"AutoTaggableObserver_Pass_All_resubscribeObserver\");\n"
        + "     group.resubscribeAll(target.resubscribeObserver);\n"
        + "     setTag(target.autoTag, \"AutoTaggableObserver_Pass_All_autoTag\");\n"
        + "  }\n"
        + "}\n"
        + ""
    );

    Truth.assertAbout(JavaSourceSubjectFactory.javaSource()).that(source)
        .withCompilerOptions("-Xlint:-processing")
        .processedWith(new ResubscriptionProcessor())
        .compilesWithoutWarnings()
        .and()
        .generatesSources(resubscriberSource);
  }

  @Test public void autoTaggableObserver_worksWithAll_customTag() throws Exception {
    JavaFileObject source = JavaFileObjects.forResource("AutoTaggableObserver_Pass_All_CustomTag.java");

    JavaFileObject resubscriberSource = JavaFileObjects.forSourceString("test.AutoTaggableObserver_Pass_All_CustomTag", ""
        + "package test;\n"
        + "import com.airbnb.rxgroups.BaseObservableResubscriber;\n"
        + "import com.airbnb.rxgroups.ObservableGroup;\n"
        + "\n"
        + "public class AutoTaggableObserver_Pass_All_CustomTag_ObservableResubscriber extends BaseObservableResubscriber {\n"
        + "  public AutoTaggableObserver_Pass_All_CustomTag_ObservableResubscriber(AutoTaggableObserver_Pass_All_CustomTag target, ObservableGroup group) {\n"
        + "     setTag(target.resubscribeObserver, \"tag1\");\n"
        + "     group.resubscribeAll(target.resubscribeObserver);\n"
        + "     setTag(target.autoTag, \"tag2\");\n"
        + "  }\n"
        + "}\n"
        + ""
    );

    Truth.assertAbout(JavaSourceSubjectFactory.javaSource()).that(source)
        .withCompilerOptions("-Xlint:-processing")
        .processedWith(new ResubscriptionProcessor())
        .compilesWithoutWarnings()
        .and()
        .generatesSources(resubscriberSource);
  }

  @Test public void plainObserverFails_autoResubscribe() throws Exception {
    JavaFileObject source = JavaFileObjects.forResource("PlainObserver_Fail_AutoResubscribe.java");


    Truth.assertAbout(JavaSourceSubjectFactory.javaSource()).that(source)
        .withCompilerOptions("-Xlint:-processing")
        .processedWith(new ResubscriptionProcessor())
        .failsToCompile()
        .withErrorContaining("AutoResubscribe annotation may only be on interface com.airbnb.rxgroups.TaggedObserver types.");
  }

  @Test public void plainObserverFails_autoTag() throws Exception {
    JavaFileObject source = JavaFileObjects.forResource("PlainObserver_Fail_AutoTag.java");


    Truth.assertAbout(JavaSourceSubjectFactory.javaSource()).that(source)
        .withCompilerOptions("-Xlint:-processing")
        .processedWith(new ResubscriptionProcessor())
        .failsToCompile()
        .withErrorContaining(" AutoTag annotation may only be on interface com.airbnb.rxgroups.AutoTaggableObserver or class com.airbnb.rxgroups.AutoResubscribingObserver types.");
  }


  @Test public void taggedObserverFails_autoTag() throws Exception {
    JavaFileObject source = JavaFileObjects.forResource("TaggedObserver_Fail_AutoTag.java");


    Truth.assertAbout(JavaSourceSubjectFactory.javaSource()).that(source)
        .withCompilerOptions("-Xlint:-processing")
        .processedWith(new ResubscriptionProcessor())
        .failsToCompile()
        .withErrorContaining("AutoTag annotation may only be on interface com.airbnb.rxgroups.AutoTaggableObserver or class com.airbnb.rxgroups.AutoResubscribingObserver types.");
  }

  @Test public void privateObserver_fail() throws Exception {
    JavaFileObject source = JavaFileObjects.forResource("AutoResubscribingObserver_Fail_Private.java");


    Truth.assertAbout(JavaSourceSubjectFactory.javaSource()).that(source)
        .withCompilerOptions("-Xlint:-processing")
        .processedWith(new ResubscriptionProcessor())
        .failsToCompile()
        .withErrorContaining("AutoResubscribe annotations must not be on private or static fields.");
  }


  @Test public void privateObserver_fail_autoTag() throws Exception {
    JavaFileObject source = JavaFileObjects.forResource("AutoResubscribingObserver_Fail_Private_AutoTag.java");


    Truth.assertAbout(JavaSourceSubjectFactory.javaSource()).that(source)
        .withCompilerOptions("-Xlint:-processing")
        .processedWith(new ResubscriptionProcessor())
        .failsToCompile()
        .withErrorContaining("AutoTag annotations must not be on private or static fields.");
  }


  @Test public void privateObserver_fail_nonStatic() throws Exception {
    JavaFileObject source = JavaFileObjects.forResource("AutoResubscribingObserver_Fail_NonStatic.java");


    Truth.assertAbout(JavaSourceSubjectFactory.javaSource()).that(source)
        .withCompilerOptions("-Xlint:-processing")
        .processedWith(new ResubscriptionProcessor())
        .failsToCompile()
        .withErrorContaining("AutoResubscribe annotations must not be on private or static fields.");
  }

}
