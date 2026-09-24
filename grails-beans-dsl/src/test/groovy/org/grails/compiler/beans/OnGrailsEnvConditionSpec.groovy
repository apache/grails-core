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
package org.grails.compiler.beans

import org.springframework.context.annotation.ConditionContext
import org.springframework.core.env.Environment
import org.springframework.core.type.AnnotatedTypeMetadata
import spock.lang.Specification

import grails.compiler.beans.ConditionalOnGrailsEnv

/**
 * The runtime half of {@code .conditionalOnGrailsEnv(...)}. The transform spec proves the
 * annotation is attached; this proves what it then does - and in particular that the paths where
 * Grails cannot answer fall back to the property rather than throwing out of {@code matches(...)},
 * which would fail the whole configuration the condition exists to skip quietly.
 */
class OnGrailsEnvConditionSpec extends Specification {

    private final OnGrailsEnvCondition condition = new OnGrailsEnvCondition()

    private AnnotatedTypeMetadata metadataFor(String... environments) {
        Stub(AnnotatedTypeMetadata) {
            getAnnotationAttributes(ConditionalOnGrailsEnv.name) >>
                    (environments == null ? null : [value: environments] as Map)
        }
    }

    private ConditionContext contextFor(String property, ClassLoader loader) {
        Stub(ConditionContext) {
            getClassLoader() >> loader
            getEnvironment() >> Stub(Environment) {
                getProperty('grails.env') >> property
            }
        }
    }

    /** A loader that answers the given Throwable for the Grails Environment class, and defers otherwise. */
    private ClassLoader loaderThrowing(Throwable failure) {
        new ClassLoader(getClass().classLoader) {
            @Override
            Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name == 'grails.util.Environment') {
                    throw failure
                }
                return super.loadClass(name, resolve)
            }
        }
    }

    void "no annotation attributes means no match"() {
        expect: "nothing to compare against, rather than an NPE"
        !condition.matches(contextFor('development', null), metadataFor(null))
    }

    void "falls back to the grails.env property when the Grails Environment class is absent"() {
        given:
        def context = contextFor('development', loaderThrowing(new ClassNotFoundException('grails.util.Environment')))

        expect:
        condition.matches(context, metadataFor('development'))
        !condition.matches(context, metadataFor('production'))
    }

    void "falls back to the property when the Environment class is present but cannot be linked"() {
        given: "a NoClassDefFoundError is a LinkageError - neither a ReflectiveOperationException nor \
               a RuntimeException, so an unbroadened catch would throw it out of matches()"
        def context = contextFor('development', loaderThrowing(new NoClassDefFoundError('some/transitive/Dep')))

        expect:
        condition.matches(context, metadataFor('development'))
        !condition.matches(context, metadataFor('production'))
    }

    void "falls back to the property when initializing the Environment class fails"() {
        given: "Class.forName initializes, so a static initializer that blows up is reachable here"
        def context = contextFor('development',
                loaderThrowing(new ExceptionInInitializerError(new IllegalStateException('boom'))))

        expect:
        condition.matches(context, metadataFor('development'))
    }

    void "matching is case-insensitive, and any one of several environments matches"() {
        given:
        def context = contextFor('DEVELOPMENT', loaderThrowing(new ClassNotFoundException('grails.util.Environment')))

        expect:
        condition.matches(context, metadataFor('development'))
        condition.matches(context, metadataFor('test', 'development'))
        !condition.matches(context, metadataFor('test', 'production'))
    }

    void "no environment name from either source means no match"() {
        given: "neither Grails nor the property can say where we are"
        def context = contextFor(null, loaderThrowing(new ClassNotFoundException('grails.util.Environment')))

        expect:
        !condition.matches(context, metadataFor('development'))
    }
}
