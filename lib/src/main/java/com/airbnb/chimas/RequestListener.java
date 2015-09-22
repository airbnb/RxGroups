package com.airbnb.chimas;

import rx.Observer;
import rx.exceptions.OnErrorFailedException;

import static com.airbnb.chimas.Util.castOrWrap;

public abstract class RequestListener<T> implements Observer<T> {

  @Override
  public void onCompleted() {
  }

  public abstract void onErrorResponse(NetworkException e);

  @Override
  public final void onError(Throwable e) {
    onErrorResponse(castOrWrap(e));
  }

  @Override
  public void onNext(T data) {
    try {
      onResponse(data);
    } catch (RuntimeException e) {
      // In case of exceptions inside the request observer/subscriber, just rethrow it as
      // OnErrorFailedException, which will cause RxJava to not route it through onError()
      // and instead just rethrow the inner exception in case it's a RuntimeException.
      // For more info, see rx.Exceptions.throwIfFatal()
      throw new OnErrorFailedException(e);
    }
  }

  public void onResponse(T data) {
  }
}
