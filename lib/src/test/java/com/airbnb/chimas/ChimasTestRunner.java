package com.airbnb.chimas;

import com.airbnb.BuildConfig;

import org.junit.runners.model.InitializationError;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.manifest.AndroidManifest;
import org.robolectric.res.Fs;

public class ChimasTestRunner extends RobolectricTestRunner {
  public static final String MANIFEST_PATH = "../lib/src/main/AndroidManifest.xml";

  public ChimasTestRunner(Class<?> testClass) throws InitializationError {
    super(testClass);

    System.setProperty("rxjava.plugin.RxJavaSchedulersHook.implementation",
        TestRxJavaSchedulerHook.class.getName());
  }

  @Override protected AndroidManifest getAppManifest(Config config) {
    String res = String.format("../lib/build/intermediates/res/merged/%1$s/%2$s",
        BuildConfig.FLAVOR, BuildConfig.BUILD_TYPE);
    String asset = "src/main/assets";
    return new AndroidManifest(Fs.fileFromPath(MANIFEST_PATH), Fs.fileFromPath(res),
        Fs.fileFromPath(asset));
  }
}
