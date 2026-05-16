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

/**
 * Verifies that {@link GrailsUtil#deepSanitize}, {@link GrailsUtil#sanitizeRootCause} and
 * {@link GrailsUtil#printSanitizedStackTrace} honour the same config keys as
 * {@code GrailsExceptionResolver} — {@code grails.logging.stackTraceFiltererClass} and
 * {@code grails.exceptionresolver.logFullStackTraceOnFilter}.
 *
 * The cached filterer is reset between scenarios via reflection so each test sees a
 * fresh lookup against its own {@link GrailsApplication}.
 */
class GrailsUtilStackFiltererSpec extends Specification {

    GrailsApplication previousApplication

    def setup() {
        previousApplication = Holders.findApplication()
        resetCachedFilterer()
    }

    def cleanup() {
        Holders.setGrailsApplication(previousApplication)
        resetCachedFilterer()
    }

    def 'falls back to a DefaultStackTraceFilterer when no GrailsApplication is discoverable'() {
        given:
        Holders.setGrailsApplication(null)

        when:
        def ex = new RuntimeException('boom')
        GrailsUtil.deepSanitize(ex)

        then:
        noExceptionThrown()
    }

    def 'honours grails.logging.stackTraceFiltererClass'() {
        given:
        def application = Mock(GrailsApplication)
        def config = Mock(Config)
        config.getProperty('grails.logging.stackTraceFiltererClass', Class, DefaultStackTraceFilterer) >> RecordingStackTraceFilterer
        config.getProperty('grails.exceptionresolver.logFullStackTraceOnFilter', Boolean, true) >> true
        application.getConfig() >> config
        Holders.setGrailsApplication(application)

        when:
        def ex = new RuntimeException('boom')
        GrailsUtil.deepSanitize(ex)

        then:
        RecordingStackTraceFilterer.lastInstance != null
        RecordingStackTraceFilterer.lastInstance.recursiveCalls == 1
    }

    def 'propagates logFullStackTraceOnFilter to DefaultStackTraceFilterer instances'() {
        given:
        def application = Mock(GrailsApplication)
        def config = Mock(Config)
        config.getProperty('grails.logging.stackTraceFiltererClass', Class, DefaultStackTraceFilterer) >> DefaultStackTraceFilterer
        config.getProperty('grails.exceptionresolver.logFullStackTraceOnFilter', Boolean, true) >> false
        application.getConfig() >> config
        Holders.setGrailsApplication(application)

        and: 'captured StackTrace logger output'
        def originalErr = System.err
        def baos = new ByteArrayOutputStream()
        System.setErr(new PrintStream(baos, true))

        when:
        GrailsUtil.deepSanitize(new RuntimeException('boom'))

        then:
        System.err.flush()
        !baos.toString().contains('ERROR StackTrace')

        cleanup:
        System.setErr(originalErr)
    }

    private static void resetCachedFilterer() {
        def field = GrailsUtil.getDeclaredField('stackFilterer')
        field.accessible = true
        field.set(null, null)
    }

    static class RecordingStackTraceFilterer implements StackTraceFilterer {
        static RecordingStackTraceFilterer lastInstance
        int singleCalls = 0
        int recursiveCalls = 0

        RecordingStackTraceFilterer() {
            lastInstance = this
        }

        Throwable filter(Throwable source) {
            singleCalls++
            return source
        }

        Throwable filter(Throwable source, boolean recursive) {
            recursiveCalls++
            return source
        }

        void addInternalPackage(String name) {}
        void setCutOffPackage(String cutOffPackage) {}
        void setShouldFilter(boolean shouldFilter) {}
    }
}
