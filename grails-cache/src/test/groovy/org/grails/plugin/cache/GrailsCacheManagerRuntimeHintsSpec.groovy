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
package org.grails.plugin.cache

import spock.lang.Specification

import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.RuntimeHintsRegistrar
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates
import org.springframework.beans.factory.aot.AotServices
import org.springframework.beans.factory.config.BeanExpressionContext
import org.springframework.beans.factory.support.AbstractBeanFactory
import org.springframework.cache.CacheManager

/**
 * The reflection a native image needs for the expression that injects the cache manager of a cache-aware class.
 */
class GrailsCacheManagerRuntimeHintsSpec extends Specification {

    void 'the registrar Spring loads from aot.factories registers what the cache manager expression reflects on'() {
        given:
        RuntimeHints hints = new RuntimeHints()
        AotServices.factories().load(RuntimeHintsRegistrar).each { RuntimeHintsRegistrar registrar ->
            registrar.registerHints(hints, getClass().classLoader)
        }

        expect:
        RuntimeHintsPredicates.reflection().onMethodInvocation(BeanExpressionContext, 'getBeanFactory').test(hints)
        RuntimeHintsPredicates.reflection().onMethodInvocation(AbstractBeanFactory, 'containsBean').test(hints)
        RuntimeHintsPredicates.reflection().onMethodInvocation(AbstractBeanFactory.getMethod('isTypeMatch', String, Class)).test(hints)
        RuntimeHintsPredicates.reflection().onType(CacheManager).test(hints)
    }
}
