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
package grails.util

import grails.config.Config
import grails.core.GrailsApplication
import org.grails.exceptions.reporting.DefaultStackTraceFilterer
import org.grails.exceptions.reporting.StackTraceFilterer
import spock.lang.Specification

import java.lang.reflect.Field

/**
 * Verifies that {@link GrailsUtil#initializeStackFilterer} resolves the configured filterer class
 * from the application's config and propagates {@code grails.exceptionresolver.logFullStackTraceOnFilter}
 * to {@link DefaultStackTraceFilterer} instances. Before initialization the FALLBACK_FILTERER
 * (a {@link DefaultStackTraceFilterer} singleton) is used so CLI/test/main paths work unchanged.
 */
class GrailsUtilStackFiltererSpec extends Specification {

    StackTraceFilterer previous

    def setup() {
        previous = currentFilterer()
        setFilterer(fallbackFilterer())
    }

    def cleanup() {
        setFilterer(previous)
    }

    def 'deepSanitize uses the fallback filterer before initializeStackFilterer is called'() {
        when:
        GrailsUtil.deepSanitize(new RuntimeException('boom'))

        then:
        noExceptionThrown()
        currentFilterer().is(fallbackFilterer())
    }

    def 'initializeStackFilterer is a no-op when application is null'() {
        when:
        GrailsUtil.initializeStackFilterer(null)

        then:
        currentFilterer().is(fallbackFilterer())
    }

    def 'initializeStackFilterer wires the class declared by grails.logging.stackTraceFiltererClass'() {
        given:
        def application = Mock(GrailsApplication)
        def config = Mock(Config)
        config.getProperty('grails.logging.stackTraceFiltererClass', Class, DefaultStackTraceFilterer) >> RecordingStackTraceFilterer
        config.getProperty('grails.exceptionresolver.logFullStackTraceOnFilter', Boolean, true) >> true
        application.getConfig() >> config

        when:
        GrailsUtil.initializeStackFilterer(application)
        GrailsUtil.deepSanitize(new RuntimeException('boom'))

        then:
        currentFilterer() instanceof RecordingStackTraceFilterer
        RecordingStackTraceFilterer.lastInstance.recursiveCalls == 1
    }

    def 'initializeStackFilterer propagates logFullStackTraceOnFilter to DefaultStackTraceFilterer instances'() {
        given:
        def application = Mock(GrailsApplication)
        def config = Mock(Config)
        config.getProperty('grails.logging.stackTraceFiltererClass', Class, DefaultStackTraceFilterer) >> DefaultStackTraceFilterer
        config.getProperty('grails.exceptionresolver.logFullStackTraceOnFilter', Boolean, true) >> false
        application.getConfig() >> config

        and: 'captured StackTrace logger output'
        def originalErr = System.err
        def baos = new ByteArrayOutputStream()
        System.setErr(new PrintStream(baos, true))

        when:
        GrailsUtil.initializeStackFilterer(application)
        GrailsUtil.deepSanitize(new RuntimeException('boom'))

        then:
        System.err.flush()
        !baos.toString().contains('ERROR StackTrace')

        cleanup:
        System.setErr(originalErr)
    }

    def 'last initializeStackFilterer call wins when invoked more than once'() {
        given:
        def first = mockApplicationFor(RecordingStackTraceFilterer)
        def second = mockApplicationFor(SecondRecordingStackTraceFilterer)

        when:
        GrailsUtil.initializeStackFilterer(first)
        GrailsUtil.initializeStackFilterer(second)

        then:
        currentFilterer() instanceof SecondRecordingStackTraceFilterer
    }

    private GrailsApplication mockApplicationFor(Class<? extends StackTraceFilterer> filtererClass) {
        def application = Mock(GrailsApplication)
        def config = Mock(Config)
        config.getProperty('grails.logging.stackTraceFiltererClass', Class, DefaultStackTraceFilterer) >> filtererClass
        config.getProperty('grails.exceptionresolver.logFullStackTraceOnFilter', Boolean, true) >> true
        application.getConfig() >> config
        application
    }

    private static StackTraceFilterer currentFilterer() {
        filtererField().get(null) as StackTraceFilterer
    }

    private static void setFilterer(StackTraceFilterer filterer) {
        filtererField().set(null, filterer)
    }

    private static StackTraceFilterer fallbackFilterer() {
        Field field = GrailsUtil.getDeclaredField('FALLBACK_FILTERER')
        field.accessible = true
        field.get(null) as StackTraceFilterer
    }

    private static Field filtererField() {
        Field field = GrailsUtil.getDeclaredField('stackFilterer')
        field.accessible = true
        field
    }

    static class RecordingStackTraceFilterer implements StackTraceFilterer {
        static RecordingStackTraceFilterer lastInstance
        int singleCalls = 0
        int recursiveCalls = 0

        RecordingStackTraceFilterer() {
            lastInstance = this
        }

        Throwable filter(Throwable source) { singleCalls++; source }
        Throwable filter(Throwable source, boolean recursive) { recursiveCalls++; source }
        void addInternalPackage(String name) {}
        void setCutOffPackage(String cutOffPackage) {}
        void setShouldFilter(boolean shouldFilter) {}
    }

    static class SecondRecordingStackTraceFilterer implements StackTraceFilterer {
        Throwable filter(Throwable source) { source }
        Throwable filter(Throwable source, boolean recursive) { source }
        void addInternalPackage(String name) {}
        void setCutOffPackage(String cutOffPackage) {}
        void setShouldFilter(boolean shouldFilter) {}
    }
}
