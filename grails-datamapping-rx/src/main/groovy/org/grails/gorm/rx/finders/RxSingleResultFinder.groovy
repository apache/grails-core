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

import java.util.regex.Pattern

import groovy.transform.CompileStatic

import grails.gorm.DetachedCriteria
import grails.gorm.rx.RxEntity
import org.grails.datastore.gorm.finders.DynamicFinder
import org.grails.datastore.gorm.finders.DynamicFinderInvocation
import org.grails.datastore.gorm.finders.FinderMethod
import org.grails.datastore.gorm.finders.MethodExpression
import org.grails.datastore.mapping.query.Query
import org.grails.datastore.rx.RxDatastoreClient
import rx.Observable
import rx.Subscriber

/**
 * Implements every RxGORM dynamic finder that returns a single result: {@code findBy*}, the
 * {@code find<booleanProperty>By*} boolean-clause form, {@code findOrCreateBy*} and
 * {@code findOrSaveBy*} - the rx-module mirror of {@link org.grails.datastore.gorm.finders.SingleResultFinder},
 * composing the same {@link DynamicFinder} grammar class rather than extending core's finder
 * classes as the previous per-type rx finder classes did.
 *
 * <p>Unlike the sync side, there is no {@code Session}/{@code Datastore.execute} concept here -
 * queries are built directly via {@link RxDatastoreClient#createQuery}, and results are
 * {@link Observable}s. The {@code findOrCreateBy*}/{@code findOrSaveBy*} empty-result handling
 * (construct-and-maybe-save via {@code Observable.switchIfEmpty}) is preserved exactly as it
 * existed in the previous per-type classes, including that it does NOT perform the
 * Or-operator/comparison-expression rejection the synchronous {@code SingleResultFinder} does -
 * that was already a pre-existing inconsistency between the sync and rx implementations, not
 * something introduced or fixed by this refactor.
 */
@CompileStatic
class RxSingleResultFinder implements FinderMethod {

    private static final String FIND_BY_PATTERN = '(findBy)([A-Z]\\w*)'
    private static final String FIND_BY_BOOLEAN_PATTERN = '(find)((\\w+)(By)([A-Z]\\w*)|(\\w++))'
    private static final String FIND_OR_CREATE_BY_PATTERN = '(findOrCreateBy)([A-Z]\\w*)'
    private static final String FIND_OR_SAVE_BY_PATTERN = '(findOrSaveBy)([A-Z]\\w*)'
    private static final String[] OPERATORS = ['And', 'Or'] as String[]

    private final RxDatastoreClient datastoreClient
    private final DynamicFinder grammar
    private final boolean handlesEmptyResult
    private final boolean save

    private RxSingleResultFinder(RxDatastoreClient datastoreClient, DynamicFinder grammar, boolean handlesEmptyResult, boolean save) {
        this.datastoreClient = datastoreClient
        this.grammar = grammar
        this.handlesEmptyResult = handlesEmptyResult
        this.save = save
    }

    static RxSingleResultFinder findBy(RxDatastoreClient datastoreClient) {
        new RxSingleResultFinder(datastoreClient, grammar(FIND_BY_PATTERN, datastoreClient, false), false, false)
    }

    static RxSingleResultFinder findByBoolean(RxDatastoreClient datastoreClient) {
        new RxSingleResultFinder(datastoreClient, grammar(FIND_BY_BOOLEAN_PATTERN, datastoreClient, true), false, false)
    }

    static RxSingleResultFinder findOrCreateBy(RxDatastoreClient datastoreClient) {
        new RxSingleResultFinder(datastoreClient, grammar(FIND_OR_CREATE_BY_PATTERN, datastoreClient, false), true, false)
    }

    static RxSingleResultFinder findOrSaveBy(RxDatastoreClient datastoreClient) {
        new RxSingleResultFinder(datastoreClient, grammar(FIND_OR_SAVE_BY_PATTERN, datastoreClient, false), true, true)
    }

    private static DynamicFinder grammar(String pattern, RxDatastoreClient datastoreClient, boolean booleanClause) {
        new DynamicFinder(Pattern.compile(pattern), OPERATORS, datastoreClient.mappingContext, booleanClause)
    }

    @Override
    void setPattern(String pattern) {
        grammar.setPattern(pattern)
    }

    @Override
    boolean isMethodMatch(String methodName) {
        grammar.isMethodMatch(methodName)
    }

    @Override
    Object invoke(Class clazz, String methodName, Object[] arguments) {
        invoke(clazz, methodName, (Closure) null, arguments)
    }

    @Override
    Object invoke(Class clazz, String methodName, Closure additionalCriteria, Object[] arguments) {
        DynamicFinderInvocation invocation = grammar.createFinderInvocation(clazz, methodName, additionalCriteria, arguments)
        doInvoke(clazz, invocation)
    }

    /**
     * Not part of {@link FinderMethod} - called reflectively (dynamic Groovy dispatch) by {@link
     * org.grails.datastore.gorm.query.criteria.AbstractDetachedCriteria#methodMissing}. Typed
     * against core's {@link DetachedCriteria} - not rx's own {@code grails.gorm.rx.DetachedCriteria}
     * - because {@code DynamicFinderInvocation.setDetachedCriteria} itself only accepts that type,
     * exactly matching what the previous per-type rx finder classes had available via inheriting
     * this same overload from core's {@code DynamicFinder}. Preserved as-is, not "fixed", since
     * changing this narrow typing is outside the scope of this refactor.
     */
    Object invoke(Class clazz, String methodName, DetachedCriteria detachedCriteria, Object[] arguments) {
        DynamicFinderInvocation invocation = grammar.createFinderInvocation(clazz, methodName, null, arguments)
        if (detachedCriteria != null) {
            invocation.setDetachedCriteria(detachedCriteria)
        }
        doInvoke(clazz, invocation)
    }

    private Object doInvoke(Class clazz, DynamicFinderInvocation invocation) {
        Query query = datastoreClient.createQuery(clazz)
        DynamicFinder.applyAdditionalCriteria(query, invocation.criteria)
        DynamicFinder.applyDetachedCriteria(query, invocation.detachedCriteria)
        DynamicFinder.configureQueryWithArguments(clazz, query, invocation.arguments)
        query.add(grammar.getJunction(invocation))
        Object result = query.singleResult()
        if (!handlesEmptyResult) {
            // Matches the previous implementation exactly: plain findBy/findByBoolean never
            // assumed/cast the query's result to Observable, it just returned whatever
            // singleResult() produced as Object and let the caller handle it.
            return result
        }
        Observable observable = (Observable) result
        return observable.switchIfEmpty(Observable.create({ Subscriber s ->
            Thread.start {
                try {
                    Map m = [:]
                    List<MethodExpression> expressions = invocation.expressions
                    for (MethodExpression me in expressions) {
                        if (!(me instanceof MethodExpression.Equal)) {
                            throw new MissingMethodException(invocation.methodName, invocation.javaClass, invocation.arguments)
                        }
                        String propertyName = me.propertyName
                        Object[] meArguments = me.getArguments()
                        m.put(propertyName, meArguments[0])
                    }

                    def newInstance = invocation.javaClass.newInstance(m)
                    if (save) {
                        def saveObservable = ((RxEntity) newInstance).save()
                        saveObservable.subscribe(new Subscriber() {
                            @Override
                            void onCompleted() {
                                s.onCompleted()
                            }

                            @Override
                            void onError(Throwable e) {
                                s.onError(e)
                            }

                            @Override
                            void onNext(Object o) {
                                s.onNext o
                            }
                        })
                    }
                    else {
                        s.onNext newInstance
                        s.onCompleted()
                    }
                }
                catch (Throwable e) {
                    // Without this, a thrown exception (e.g. MissingMethodException from the
                    // non-Equal-expression check above, or a failure constructing newInstance)
                    // kills this spawned Thread silently: it never reaches the Observable's error
                    // channel, so any blocking call on the returned Observable hangs forever.
                    s.onError(e)
                }
            }
        } as Observable.OnSubscribe))
    }
}
