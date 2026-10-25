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

    void "a value that is not a list of environment names means no match"() {
        given:
        def metadata = Stub(AnnotatedTypeMetadata) {
            getAnnotationAttributes(ConditionalOnGrailsEnv.name) >> ([value: 'development'] as Map)
        }

        expect:
        !condition.matches(contextFor('development', loaderThrowing(new ClassNotFoundException('grails.util.Environment'))), metadata)
    }

    void "a null class loader resolves the environment through the default loader"() {
        given: "Grails is on this classpath, so the environment it reports is the one that decides"
        String name = grails.util.Environment.current.name

        expect:
        condition.matches(contextFor('some-other-env', null), metadataFor(name))
        !condition.matches(contextFor(name, null), metadataFor('not-' + name))
    }

    void "the environment Grails reports wins over the grails.env property"() {
        given:
        String name = grails.util.Environment.current.name

        expect:
        condition.matches(contextFor('production', getClass().classLoader), metadataFor(name))
    }

    void "an Environment that has no current environment falls back to the property"() {
        given:
        def context = contextFor('development', environmentLoader('static Object getCurrent() { null }'))

        expect:
        condition.matches(context, metadataFor('development'))
        !condition.matches(context, metadataFor('production'))
    }

    void "a current environment without a name falls back to the property"() {
        given:
        def context = contextFor('development', environmentLoader(
                'static Object getCurrent() { new Environment() }\nObject getName() { null }'))

        expect:
        condition.matches(context, metadataFor('development'))
    }

    void "an environment name from Grails is lower-cased before it is compared"() {
        given:
        def context = contextFor(null, environmentLoader(
                'static Object getCurrent() { new Environment() }\nObject getName() { \'STAGING\' }'))

        expect:
        condition.matches(context, metadataFor('staging'))
        !condition.matches(context, metadataFor('production'))
    }

    void "an Environment that fails when asked for its name falls back to the property"() {
        given:
        def context = contextFor('development', environmentLoader(
                'static Object getCurrent() { new Environment() }\nObject getName() { throw new IllegalStateException(\'boom\') }'))

        expect:
        condition.matches(context, metadataFor('development'))
    }

    /**
     * A loader that answers {@code grails.util.Environment} with a class compiled from the given
     * members, so the condition meets an Environment that behaves as the test needs.
     */
    private ClassLoader environmentLoader(String members) {
        GroovyClassLoader compiler = new GroovyClassLoader(getClass().classLoader)
        Class<?> fake = compiler.parseClass("package grails.util\nclass Environment {\n${members}\n}")
        new ClassLoader(getClass().classLoader) {
            @Override
            Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                name == 'grails.util.Environment' ? fake : super.loadClass(name, resolve)
            }
        }
    }

}
