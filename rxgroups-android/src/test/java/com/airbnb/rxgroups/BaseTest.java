package com.airbnb.rxgroups;

import org.junit.BeforeClass;
import org.robolectric.shadows.ShadowLog;

import rx.android.plugins.RxAndroidPlugins;

public abstract class BaseTest {
  @BeforeClass public static void classSetUp() {
    ShadowLog.stream = System.out;
    RxAndroidPlugins instance = RxAndroidPlugins.getInstance();
    instance.reset();
    instance.registerSchedulersHook(new TestRxAndroidSchedulersHook());
  }
}
