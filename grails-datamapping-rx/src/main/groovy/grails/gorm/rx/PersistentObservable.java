/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package grails.gorm.rx;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.ObservableSource;
import io.reactivex.rxjava3.core.Observer;

/**
 * Common interface for persistent related observables to implement such as collections and proxies
 *
 * @since 6.0
 */
public interface PersistentObservable<T> extends ObservableSource<T> {

    /**
     * Returns an Observable for the operation
     *<p>
     * For more information on Observables see the
     * <a href="http://reactivex.io/documentation/observable.html">ReactiveX documentation</a>.
     *</p>
     *
     * @return the Observable for the operation
     */
    Observable<T> toObservable();

    /**
     * A convenience method that subscribes to the Observable as provided by {@link #toObservable}.
     *
     * <p>
     * For more information on Subscriptions see the
     * <a href="http://reactivex.io/documentation/observable.html">ReactiveX documentation</a>.
     *</p>
     *
     * @param observer the Observer that will handle emissions and notifications from the Observable; the
     *                 {@link io.reactivex.rxjava3.disposables.Disposable} it receives in {@code onSubscribe} disposes the subscription
     */
    @Override
    void subscribe(Observer<? super T> observer);
}
