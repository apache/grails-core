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
package org.grails.exception.reporting

import spock.lang.Specification

import org.grails.exceptions.reporting.DefaultStackTraceFilterer
import org.grails.exceptions.reporting.StackTraceFilterer
import org.apache.grails.core.testing.support.LogCapture

class StackTraceFiltererSpec extends Specification {

    StackTraceFilterer filterer = new DefaultStackTraceFilterer()
    ClassLoader gcl = new GroovyClassLoader()

    def 'retains application frames when filtering a stack trace'() {
        given: 'a controller action that raises a missing property exception'
        def controller = gcl.parseClass('''
            package test

            class FooController {
                def show = {
                    display()
                }

                void display() {
                    notHere
                }
            }
        ''').getDeclaredConstructor().newInstance()

        when: 'the exception stack trace is filtered'
        Throwable exception = null
        try {
            controller['show']()
        } catch (e) {
            filterer.filter(e)
            exception = e
        }

        then: 'the exception is available for inspection'
        exception != null

        and: 'the controller action and helper method frames remain in the filtered stack trace'
        with(exception.stackTrace) {
            it.find { it.className == 'test.FooController' && it.lineNumber == 10 }
            it.find { it.className.startsWith('test.FooController') && it.lineNumber == 6 }
        }
    }

    def 'filter trims a single throwable without logging by default'() {
        given: 'a configured log appender to capture the StackTrace log entry'
            def logCapture = new LogCapture('StackTrace')

        and: 'an exception whose stack trace mixes application and internal frames'
            def exception = new RuntimeException('boom')
            exception.stackTrace = [
                ['test.FooController', 'show', 'FooController.groovy', 6],
                ['java.lang.reflect.Method', 'invoke', 'Method.java', 580]
            ] as StackTraceElement[]

        when: 'the exception is filtered'
            filterer.filter(exception)

        then: 'filtering is silent and retains only application frames'
            logCapture.events.empty
            exception.stackTrace*.className == ['test.FooController']

        cleanup:
            logCapture.close()
    }

    def 'filter ignores the legacy logFullStackTraceOnFilter value #legacyValue'() {
        given: 'a configured log appender to capture the StackTrace log entry'
            def logCapture = new LogCapture('StackTrace')

        and: 'a filterer configured through the compatibility setter'
            def quietFilterer = new DefaultStackTraceFilterer(logFullStackTraceOnFilter: legacyValue)

        and: 'an exception whose stack trace mixes application and internal frames'
            def exception = new RuntimeException('boom')
            exception.stackTrace = [
                ['test.FooController', 'show', 'FooController.groovy', 6],
                ['java.lang.reflect.Method', 'invoke', 'Method.java', 580]
            ] as StackTraceElement[]

        when: 'the exception is filtered'
            quietFilterer.filter(exception)

        then: "no 'Full Stack Trace:' entry is emitted by the filterer"
            logCapture.events.count { it.formattedMessage.contains('Full Stack Trace:') } == 0

        cleanup:
            logCapture.close()

        where:
            legacyValue << [true, false]
    }

    def 'retains controller frames across wrapped exceptions during recursive filtering'() {
        given: 'a controller action that wraps a failure triggered during service interaction'
            def controller = gcl.parseClass('''
                package test

                class FooController {
                    def fooService = new FooService()
                    def show = {
                        display()
                    }

                    void display() {
                        try {
                            fooService.notThere()
                        }
                        catch(e) {
                            throw new RuntimeException("Bad things happened", e)
                        }

                    }
                }
                class FooService {
                    void doStuff() {
                        notThere()
                    }
                }
            ''').getDeclaredConstructor().newInstance()

        when: 'recursive filtering is applied to the exception'
           Throwable exception = null
           try {
               controller['show']()
           } catch (e) {
               filterer.filter(e, true)
               exception = e
           }

        then: 'the wrapped exception is available for inspection'
            exception != null

        and: 'the filtered stack trace retains the controller frames for the wrapper and action'
            with(exception.stackTrace) {
                it.find { it.className == 'test.FooController' && it.lineNumber == 15 }
                it.find { it.className.startsWith('test.FooController') && it.lineNumber == 7 }
            }
    }

    def 'recursive filtering trims the entire cause chain without logging by default'() {
        given: 'a configured log appender to capture the StackTrace log entry'
            def logCapture = new LogCapture('StackTrace')

        and: 'a wrapped exception whose wrapper and cause mix application and internal frames'
            def rootCause = new IllegalStateException('root cause')
            rootCause.stackTrace = [
                ['test.FooService', 'doStuff', 'FooService.groovy', 3],
                ['java.lang.reflect.Method', 'invoke', 'Method.java', 580]
            ] as StackTraceElement[]

            def exception = new RuntimeException('boom', rootCause)
            exception.stackTrace = [
                ['test.FooController', 'show', 'FooController.groovy', 6],
                ['java.lang.reflect.Method', 'invoke', 'Method.java', 580]
            ] as StackTraceElement[]

        when: 'recursive filtering is applied to the top-level exception'
            filterer.filter(exception, true)

        then: 'no log entries are emitted for any throwable in the chain'
            logCapture.events.empty
            exception.stackTrace*.className == ['test.FooController']
            rootCause.stackTrace*.className == ['test.FooService']

        cleanup:
            logCapture.close()
    }

    def 'recursive filtering ignores the legacy logFullStackTraceOnFilter value #legacyValue'() {
        given: 'a configured log appender to capture the StackTrace log entry'
            def logCapture = new LogCapture('StackTrace')

        and: 'a filterer configured through the compatibility setter'
            def quietFilterer = new DefaultStackTraceFilterer(logFullStackTraceOnFilter: legacyValue)

        and: 'a wrapped exception whose wrapper and cause mix application and internal frames'
            def rootCause = new IllegalStateException('root cause')
            rootCause.stackTrace = [
                ['test.FooService', 'doStuff', 'FooService.groovy', 3],
                ['java.lang.reflect.Method', 'invoke', 'Method.java', 580]
            ] as StackTraceElement[]

            def exception = new RuntimeException('boom', rootCause)
            exception.stackTrace = [
                ['test.FooController', 'show', 'FooController.groovy', 6],
                ['java.lang.reflect.Method', 'invoke', 'Method.java', 580]
            ] as StackTraceElement[]

        when: 'recursive filtering is applied to the top-level exception'
            quietFilterer.filter(exception, true)

        then: "no 'Full Stack Trace:' entry is emitted for any throwable in the chain"
            logCapture.events.count { it.formattedMessage.contains('Full Stack Trace:') } == 0

        cleanup:
            logCapture.close()

        where:
            legacyValue << [true, false]
    }

    def 'recursive filtering visits every throwable in the cause chain and sanitizes each'() {
        given: 'a cause chain with both application and internal stack frames'
            def filterer = new CountingStackTraceFilterer()
            def rootCause = new IllegalStateException('root cause')
            rootCause.stackTrace = [
                ['test.FooService', 'doStuff', 'FooService.groovy', 3],
                ['org.codehaus.groovy.runtime.InvokerHelper', 'invokeMethod', 'InvokerHelper.java', 12]
            ] as StackTraceElement[]

            def wrappedCause = new RuntimeException('wrapped cause', rootCause)
            wrappedCause.stackTrace = [
                ['test.FooController', 'display', 'FooController.groovy', 11],
                ['org.codehaus.groovy.runtime.callsite.CallSiteArray', 'defaultCall', 'CallSiteArray.java', 15]
            ] as StackTraceElement[]

            def exception = new RuntimeException('top level', wrappedCause)
            exception.stackTrace = [
                ['test.FooController', 'show', 'FooController.groovy', 7],
                ['org.codehaus.groovy.runtime.ScriptBytecodeAdapter', 'unwrap', 'ScriptBytecodeAdapter.java', 20]
            ] as StackTraceElement[]

        when: 'recursive filtering is applied to the top-level exception'
            filterer.filter(exception, true)

        then: 'filter is invoked once per throwable in the cause chain, in cause-chain order'
            with(filterer) {
                singleExceptionFilterInvocations == 3
                filteredSources == [exception, wrappedCause, rootCause]
            }

        and: 'application stack frames are retained across the full cause chain'
            with(exception) {
                stackTrace*.className == ['test.FooController']
                stackTrace*.lineNumber == [7]
                cause.stackTrace*.className == ['test.FooController']
                cause.stackTrace*.lineNumber == [11]
                cause.cause.stackTrace*.className == ['test.FooService']
                cause.cause.stackTrace*.lineNumber == [3]
            }
    }

    private static class CountingStackTraceFilterer extends DefaultStackTraceFilterer {

        int singleExceptionFilterInvocations
        List<Throwable> filteredSources = []

        CountingStackTraceFilterer() {
            super(true)
        }

        @Override
        Throwable filter(Throwable source) {
            singleExceptionFilterInvocations++
            filteredSources << source
            super.filter(source)
        }
    }
}
