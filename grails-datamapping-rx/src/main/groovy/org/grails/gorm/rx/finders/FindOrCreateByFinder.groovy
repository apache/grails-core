/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.grails.gorm.rx.finders

import grails.gorm.rx.RxEntity
import groovy.transform.CompileStatic
import org.grails.datastore.gorm.finders.DynamicFinderInvocation
import org.grails.datastore.gorm.finders.MethodExpression
import org.grails.datastore.rx.RxDatastoreClient
import rx.Observable
import rx.Observer
import rx.Subscriber

/**
 * Implementation of findOrCreateBy* finder for RxGORM
 *
 * @author Graeme Rocher
 * @since 6.0
 */
@CompileStatic
class FindOrCreateByFinder extends FindByFinder {
    FindOrCreateByFinder(RxDatastoreClient datastoreClient) {
        super(datastoreClient)
        setPattern(org.grails.datastore.gorm.finders.FindOrCreateByFinder.METHOD_PATTERN)
    }

    @Override
    protected Object doInvokeInternal(DynamicFinderInvocation invocation) {
        Observable observable = (Observable)super.doInvokeInternal(invocation)
        observable.switchIfEmpty(Observable.create( { Subscriber s ->
                Thread.start {
                    Map m = [:]
                    List<MethodExpression> expressions = invocation.getExpressions()
                    for (MethodExpression me in expressions) {
                        if (!(me instanceof MethodExpression.Equal)) {
                            throw new MissingMethodException(invocation.methodName, invocation.javaClass, invocation.arguments)
                        }
                        String propertyName = me.propertyName
                        Object[] arguments = me.getArguments()
                        m.put(propertyName, arguments[0])
                    }

                    def newInstance = invocation.javaClass.newInstance(m)
                    if(shouldSaveOnCreate()) {
                        def saveObservable = ((RxEntity) newInstance).save()
                        saveObservable.subscribe(new Subscriber() {
                            @Override
                            void onCompleted() {
                                s.onCompleted()
                            }

                            @Override
                            void onError(Throwable e) {
                                s.onCompleted()
                            }

                            @Override
                            void onNext(Object o) {
                                s.onNext o
                            }
                        })
                    }
                    else {
                        s.onNext newInstance
                    }
                }

            } as Observable.OnSubscribe)
        )
    }

    protected boolean shouldSaveOnCreate() {
        return false
    }
}
