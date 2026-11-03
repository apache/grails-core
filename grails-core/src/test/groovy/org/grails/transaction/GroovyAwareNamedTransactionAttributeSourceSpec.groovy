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
package org.grails.transaction

import spock.lang.Specification
import spock.lang.Unroll

import java.lang.reflect.Method

class GroovyAwareNamedTransactionAttributeSourceSpec extends Specification {

    void 'a compiler-generated synthetic bridge method never receives a transaction attribute'() {
        given:
        GroovyAwareNamedTransactionAttributeSource source = new GroovyAwareNamedTransactionAttributeSource()
        Properties properties = new Properties()
        properties.setProperty('handle', 'PROPAGATION_REQUIRED')
        source.setTransactionalAttributes(properties)
        Method synthetic = StringHandler.declaredMethods.find { it.synthetic && it.name == 'handle' }

        expect:
        synthetic != null
        source.getTransactionAttribute(synthetic, StringHandler) == null
    }

    @Unroll
    void 'Groovy dynamic-dispatch method #methodName never matches a configured pattern'() {
        given:
        GroovyAwareNamedTransactionAttributeSourceTestSubject source = new GroovyAwareNamedTransactionAttributeSourceTestSubject()

        expect:
        !source.isMatchPublic(methodName, '*')

        where:
        methodName << ['invokeMethod', 'getMetaClass', 'getProperty', 'setProperty']
    }

    void 'an ordinary method name still matches the wildcard pattern'() {
        given:
        GroovyAwareNamedTransactionAttributeSourceTestSubject source = new GroovyAwareNamedTransactionAttributeSourceTestSubject()

        expect:
        source.isMatchPublic('saveOrder', '*')
    }

    void 'setTransactionalAttributes wires properties through to the underlying NameMatchTransactionAttributeSource'() {
        given:
        GroovyAwareNamedTransactionAttributeSource source = new GroovyAwareNamedTransactionAttributeSource()
        Properties properties = new Properties()
        properties.setProperty('save*', 'PROPAGATION_REQUIRED')

        when:
        source.setTransactionalAttributes(properties)

        then:
        source.getTransactionAttribute(GroovyAwareNamedTransactionAttributeSourceSpec.getMethod('saveSomething'), null) != null
    }

    static void saveSomething() {
    }

    static class GenericHandler<T> {

        void handle(T t) {
        }
    }

    static class StringHandler extends GenericHandler<String> {

        @Override
        void handle(String t) {
        }
    }

    static class GroovyAwareNamedTransactionAttributeSourceTestSubject extends GroovyAwareNamedTransactionAttributeSource {

        boolean isMatchPublic(String methodName, String mappedName) {
            return isMatch(methodName, mappedName)
        }
    }
}
