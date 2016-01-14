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
import rx.Observer;
import rx.android.schedulers.AndroidSchedulers;

public class MainActivity extends AppCompatActivity {
  private static final String GROUP_ID = "GROUP_ID";
  private static final String OBSERVABLE_TAG = "timer";
  private static final String TAG = "MainActivity";
  private static final String IS_RUNNING = "IS_RUNNING";

  private ObservableGroup observableGroup;
  private ObservableManager observableManager;
  private TextView output;
  private Observable<Long> timerObservable;
  private boolean isRunning;
  private boolean isLocked;
  private final Observer<Long> observer = new Observer<Long>() {
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

    startStop.setOnClickListener(this::onClickStartStopTimer);
    lockUnlock.setOnClickListener(this::onClickLockUnlockGroup);

    SampleApplication application = (SampleApplication) getApplication();
    observableManager = application.observableManager();
    timerObservable = application.timerObservable();

    if (savedInstanceState == null) {
      observableGroup = observableManager.newGroup();
    } else {
      // This doesn't quite work 100% of the time, since in some specific scenarios (eg.: your app
      // was killed while in background), when your activity is recreated, your process is also
      // recreated, which causes ObservableManager to be empty (no groups). In this case, getGroup()
      // would crash since it's obviously not there. It is left as an exercise for the reader on
      // how to work around this situation.
      observableGroup = observableManager.getGroup(savedInstanceState.getLong(GROUP_ID));
      if (savedInstanceState.getBoolean(IS_RUNNING)) {
        isRunning = true;
        startStop.setImageDrawable(alarmOffDrawable);
      }
    }

    // Make sure no events are received until we are ready to display them. Locking the group
    // will cache any results so they can be delivered immediately when you unlock() if there are
    // any available
    observableGroup.lock();

    if (observableGroup.hasObservable(OBSERVABLE_TAG)) {
      observableGroup.<Long>observable(OBSERVABLE_TAG)
          .onBackpressureBuffer()
          .observeOn(AndroidSchedulers.mainThread())
          .subscribe(observer);
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
          .compose(observableGroup.<Long>transform(OBSERVABLE_TAG))
          .observeOn(AndroidSchedulers.mainThread())
          .onBackpressureBuffer()
          .subscribe(observer);
    } else {
      Toast.makeText(this, "Stopped timer", Toast.LENGTH_SHORT).show();
      isRunning = false;
      startStop.setImageDrawable(alarmDrawable);
      observableGroup.cancelAndRemove(OBSERVABLE_TAG);
    }
  }

  @Override protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putLong(GROUP_ID, observableGroup.id());
    outState.putBoolean(IS_RUNNING, isRunning);
  }

  @Override protected void onResume() {
    super.onResume();
    Log.d(TAG, "onResume()");
    // Go ahead and unlock our group, so we can receive Observable events.
    observableGroup.unlock();
  }

  @Override protected void onPause() {
    super.onPause();
    Log.d(TAG, "onPause()");
    observableGroup.lock();
  }

  @Override protected void onDestroy() {
    super.onDestroy();
    Log.d(TAG, "onDestroy()");
    if (isFinishing()) {
      // Unsubscribe Observers and clear references. No more events will be received and the
      // destroyed ObservableGroup is now unusable.
      observableManager.destroy(observableGroup);
    } else {
      // Activity is not finishing (maybe just rotating?) so just unsubscribe for now and assume
      // that it will be resubscribed later
      observableGroup.unsubscribe();
    }
  }
}
