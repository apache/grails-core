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

import org.apache.grails.core.testing.support.LogCapture
import org.grails.exceptions.reporting.DefaultStackTraceFilterer
import org.grails.exceptions.reporting.StackTraceFilterer
import spock.lang.Specification

/**
 * Verifies that {@link GrailsUtil#initializeStackFilterer(StackTraceFilterer)} installs the given
 * filterer for {@link GrailsUtil#deepSanitize}, {@link GrailsUtil#sanitizeRootCause} and
 * {@link GrailsUtil#printSanitizedStackTrace}. Config-driven resolution (the configured class +
 * {@code logFullStackTraceOnFilter}) happens in
 * {@code org.apache.grails.core.GrailsBootstrapRegistryInitializer}, covered separately by
 * {@code GrailsBootstrapRegistryInitializerSpec}.
 *
 * <p>Every assertion here is behavioural — which filterer a public {@code GrailsUtil} sanitize call
 * routes to, and what it emits — so nothing depends on the shape of the class's internal state.
 */
class GrailsUtilStackFiltererSpec extends Specification {

    def cleanup() {
        GrailsUtil.initializeStackFilterer(new DefaultStackTraceFilterer())
    }

    def 'deepSanitize works with the default filterer installed'() {
        given:
        GrailsUtil.initializeStackFilterer(new DefaultStackTraceFilterer())

        when:
        GrailsUtil.deepSanitize(new RuntimeException('boom'))

        then:
        noExceptionThrown()
    }

    def 'initializeStackFilterer installs the given filterer'() {
        given:
        def filterer = new RecordingStackTraceFilterer()

        when:
        GrailsUtil.initializeStackFilterer(filterer)
        GrailsUtil.deepSanitize(new RuntimeException('boom'))

        then:
        filterer.recursiveCalls == 1
    }

    def 'the installed filterer also serves sanitizeRootCause and printSanitizedStackTrace'() {
        given:
        def filterer = new RecordingStackTraceFilterer()

        when:
        GrailsUtil.initializeStackFilterer(filterer)
        GrailsUtil.sanitizeRootCause(new RuntimeException('boom'))
        GrailsUtil.printSanitizedStackTrace(new RuntimeException('boom'), new PrintWriter(new StringWriter()))

        then:
        filterer.singleCalls == 2
    }

    def 'initializeStackFilterer is a no-op when filterer is null'() {
        given:
        def filterer = new RecordingStackTraceFilterer()
        GrailsUtil.initializeStackFilterer(filterer)

        when:
        GrailsUtil.initializeStackFilterer(null)
        GrailsUtil.deepSanitize(new RuntimeException('boom'))

        then: 'the previously installed filterer is still the one in use'
        filterer.recursiveCalls == 1
    }

    def 'last initializeStackFilterer call wins when invoked more than once'() {
        given:
        def first = new RecordingStackTraceFilterer()
        def second = new RecordingStackTraceFilterer()

        when:
        GrailsUtil.initializeStackFilterer(first)
        GrailsUtil.initializeStackFilterer(second)
        GrailsUtil.deepSanitize(new RuntimeException('boom'))

        then:
        second.recursiveCalls == 1
        first.recursiveCalls == 0
    }

    def 'installed DefaultStackTraceFilterer honours logFullStackTraceOnFilter=false'() {
        given: 'a configured log appender to capture the StackTrace log entry'
        def logCapture = new LogCapture('StackTrace')

        and: 'a filterer with the side-effect emission disabled'
        def quietFilterer = new DefaultStackTraceFilterer()
        quietFilterer.logFullStackTraceOnFilter = false

        when:
        GrailsUtil.initializeStackFilterer(quietFilterer)
        GrailsUtil.deepSanitize(exceptionWithApplicationFrame())

        then: "no 'Full Stack Trace:' entry is emitted"
        logCapture.events.count { it.formattedMessage.contains(StackTraceFilterer.FULL_STACK_TRACE_MESSAGE) } == 0

        cleanup:
        logCapture.close()
    }

    def 'installed DefaultStackTraceFilterer emits Full Stack Trace by default'() {
        given: 'a configured log appender to capture the StackTrace log entry'
        def logCapture = new LogCapture('StackTrace')

        and: 'a filterer with the default (enabled) side-effect emission'
        def loudFilterer = new DefaultStackTraceFilterer()

        when:
        GrailsUtil.initializeStackFilterer(loudFilterer)
        GrailsUtil.deepSanitize(exceptionWithApplicationFrame())

        then: "a 'Full Stack Trace:' entry is emitted -- the positive control proving the negative case above is meaningful"
        logCapture.events.any { it.formattedMessage.contains(StackTraceFilterer.FULL_STACK_TRACE_MESSAGE) }

        cleanup:
        logCapture.close()
    }

    private static RuntimeException exceptionWithApplicationFrame() {
        def exception = new RuntimeException('boom')
        exception.stackTrace = [
                new StackTraceElement('test.FooController', 'show', 'FooController.groovy', 6),
                new StackTraceElement('java.lang.reflect.Method', 'invoke', 'Method.java', 580)
        ] as StackTraceElement[]
        exception
    }

    static class RecordingStackTraceFilterer implements StackTraceFilterer {
        int singleCalls = 0
        int recursiveCalls = 0

        Throwable filter(Throwable source) { singleCalls++; source }
        Throwable filter(Throwable source, boolean recursive) { recursiveCalls++; source }
        void addInternalPackage(String name) {}
        void setCutOffPackage(String cutOffPackage) {}
        void setShouldFilter(boolean shouldFilter) {}
    }
}
