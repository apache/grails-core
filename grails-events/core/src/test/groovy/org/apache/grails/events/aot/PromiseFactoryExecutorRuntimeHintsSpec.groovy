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
package org.apache.grails.events.aot

import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates
import org.springframework.beans.BeanWrapperImpl
import spock.lang.Specification

import org.grails.async.factory.future.CompletableFuturePromiseFactory
import org.grails.async.factory.future.VirtualThreadPromiseFactory

/**
 * Covers the executor the event bus runs on being readable in an image, where the event bus reads it from the
 * promise factory as a bean property.
 */
class PromiseFactoryExecutorRuntimeHintsSpec extends Specification {

    RuntimeHints hints = new RuntimeHints()

    void setup() {
        new PromiseFactoryExecutorRuntimeHints().registerHints(hints, getClass().classLoader)
    }

    void 'the executor of #factory.simpleName can be read'() {
        expect: 'reading a bean property is a reflective call to its getter'
            RuntimeHintsPredicates.reflection().onType(factory).withMemberCategories(MemberCategory.INVOKE_PUBLIC_METHODS).test(hints)

        where:
            factory << [CompletableFuturePromiseFactory, VirtualThreadPromiseFactory]
    }

    void 'the property registered is the one the event bus reads'() {
        given:
            def promiseFactory = new CompletableFuturePromiseFactory()

        expect:
            new BeanWrapperImpl(promiseFactory).isReadableProperty('executor')

        cleanup:
            promiseFactory.close()
    }

    void 'a factory that is not on the classpath is skipped'() {
        expect: 'the async module providing the third is not a dependency here'
            hints.reflection().typeHints().noneMatch { it.type.name == 'org.grails.async.factory.groovy.AwaitablePromiseFactory' }
    }
}
