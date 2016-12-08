/*
 * Copyright (C) 2016 Airbnb, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.airbnb.rxgroups;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;

public class MainActivity extends AppCompatActivity {
  private static final String IS_RUNNING = "IS_RUNNING";
  private static final String TAG = "MainActivity";

  private GroupLifecycleManager groupLifecycleManager;
  private TextView output;
  private Observable<Long> timerObservable;
  private boolean isRunning;
  private boolean isLocked;

  @AutoResubscribe final AutoResubscribingObserver<Long> observer =
      new AutoResubscribingObserver<Long>() {
        @Override public void onCompleted() {
          Log.d(TAG, "onCompleted()");
        }

        @Override public void onError(Throwable e) {
          Log.e(TAG, "onError()", e);
        }

        @Override public void onNext(Long l) {
          Log.d(TAG, "Current Thread=" + Thread.currentThread().getName() + ", onNext()=" + l);
          output.setText(output.getText() + " " + l);
        }
      };

  private Drawable alarmOffDrawable;
  private Drawable alarmDrawable;
  private Drawable lockDrawable;
  private Drawable unlockDrawable;
  private FloatingActionButton startStop;
  private FloatingActionButton lockUnlock;
  private ObservableGroup observableGroup;

  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
    startStop = (FloatingActionButton) findViewById(R.id.fab);
    lockUnlock = (FloatingActionButton) findViewById(R.id.fab_pause_resume);
    alarmOffDrawable = ContextCompat.getDrawable(this, R.drawable.ic_alarm_off_black_24dp);
    alarmDrawable = ContextCompat.getDrawable(this, R.drawable.ic_alarm_black_24dp);
    lockDrawable = ContextCompat.getDrawable(this, R.drawable.ic_lock_outline_black_24dp);
    unlockDrawable = ContextCompat.getDrawable(this, R.drawable.ic_lock_open_black_24dp);
    output = (TextView) findViewById(R.id.txt_output);
    setSupportActionBar(toolbar);

    startStop.setOnClickListener(new View.OnClickListener() {
      @Override public void onClick(View v) {
        onClickStartStopTimer(v);
      }
    });
    lockUnlock.setOnClickListener(new View.OnClickListener() {
      @Override public void onClick(View v) {
        onClickLockUnlockGroup(v);
      }
    });

    SampleApplication application = (SampleApplication) getApplication();
    ObservableManager manager = application.observableManager();
    groupLifecycleManager = GroupLifecycleManager.onCreate(manager, savedInstanceState, this);
    timerObservable = application.timerObservable();
    observableGroup = groupLifecycleManager.group();

    if (savedInstanceState != null && savedInstanceState.getBoolean(IS_RUNNING)) {
      isRunning = true;
      startStop.setImageDrawable(alarmOffDrawable);
    }
  }

  private void onClickLockUnlockGroup(View view) {
    if (isRunning) {
      if (isLocked) {
        Toast.makeText(this, "Group unlocked", Toast.LENGTH_SHORT).show();
        lockUnlock.setImageDrawable(unlockDrawable);
        observableGroup.unlock();
      } else {
        Toast.makeText(this, "Group locked", Toast.LENGTH_SHORT).show();
        lockUnlock.setImageDrawable(lockDrawable);
        observableGroup.lock();
      }
      isLocked = !isLocked;
    }
  }

  private void onClickStartStopTimer(View view) {
    if (!isRunning) {
      Toast.makeText(this, "Started timer", Toast.LENGTH_SHORT).show();
      isRunning = true;
      startStop.setImageDrawable(alarmOffDrawable);
      timerObservable
          .observeOn(AndroidSchedulers.mainThread())
          .onBackpressureBuffer()
          .compose(groupLifecycleManager.<Long>transform(observer))
          .subscribe(observer);
    } else {
      Toast.makeText(this, "Stopped timer", Toast.LENGTH_SHORT).show();
      isRunning = false;
      startStop.setImageDrawable(alarmDrawable);
      observableGroup.cancelAndRemove(observer);
    }
  }

  @Override protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    groupLifecycleManager.onSaveInstanceState(outState);
    outState.putBoolean(IS_RUNNING, isRunning);
  }

  @Override protected void onResume() {
    super.onResume();
    Log.d(TAG, "onResume()");
    groupLifecycleManager.onResume();
  }

  @Override protected void onPause() {
    super.onPause();
    Log.d(TAG, "onPause()");
    groupLifecycleManager.onPause();
  }

  @Override protected void onDestroy() {
    super.onDestroy();
    Log.d(TAG, "onDestroy()");
    groupLifecycleManager.onDestroy(this);
  }
}
