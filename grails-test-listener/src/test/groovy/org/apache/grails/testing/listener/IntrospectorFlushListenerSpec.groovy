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
package org.apache.grails.testing.listener

import java.beans.BeanInfo
import java.beans.Introspector

import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.engine.TestSource
import org.junit.platform.engine.UniqueId
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor
import org.junit.platform.engine.support.descriptor.ClassSource
import org.junit.platform.engine.support.descriptor.EngineDescriptor
import org.junit.platform.engine.support.descriptor.MethodSource
import org.junit.platform.launcher.TestExecutionListener
import org.junit.platform.launcher.TestIdentifier
import spock.lang.Specification

class IntrospectorFlushListenerSpec extends Specification {

    static class Bean {
        String name
    }

    void 'the listener is registered for the JUnit Platform launcher to load'() {
        expect:
        ServiceLoader.load(TestExecutionListener).stream().anyMatch { it.type() == IntrospectorFlushListener }
    }

    void 'the BeanInfo cached during a test class is released when the class finishes'() {
        given:
        BeanInfo cached = Introspector.getBeanInfo(Bean)

        when:
        new IntrospectorFlushListener().executionFinished(identifier(TestDescriptor.Type.CONTAINER, ClassSource.from(Bean)), TestExecutionResult.successful())

        then:
        !Introspector.getBeanInfo(Bean).is(cached)
    }

    void 'the cache is kept when a #type with a #source.class.simpleName finishes'() {
        given:
        BeanInfo cached = Introspector.getBeanInfo(Bean)

        when:
        new IntrospectorFlushListener().executionFinished(identifier(type, source), TestExecutionResult.successful())

        then:
        Introspector.getBeanInfo(Bean).is(cached)

        where:
        type                             | source
        TestDescriptor.Type.TEST         | MethodSource.from(Bean.name, 'getName')
        TestDescriptor.Type.CONTAINER    | MethodSource.from(Bean.name, 'getName')
        TestDescriptor.Type.TEST         | ClassSource.from(Bean)
    }

    void 'the cache is kept when the engine finishes'() {
        given:
        final cached = Introspector.getBeanInfo(Bean)

        when:
        new IntrospectorFlushListener().executionFinished(TestIdentifier.from(new EngineDescriptor(UniqueId.forEngine('probe'), 'probe')), TestExecutionResult.successful())

        then:
        Introspector.getBeanInfo(Bean).is(cached)
    }

    private static TestIdentifier identifier(TestDescriptor.Type type, TestSource source) {
        TestIdentifier.from(new AbstractTestDescriptor(UniqueId.forEngine('probe'), 'probe', source) {
            @Override
            TestDescriptor.Type getType() {
                type
            }
        })
    }
}
