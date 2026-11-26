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

import org.grails.exceptions.reporting.DefaultStackTraceFilterer
import spock.lang.Specification

import org.grails.exceptions.reporting.StackTraceFilterer

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

    def 'filter emits a StackTrace log entry for a single throwable by default'() {
        given: 'captured System.err'
            def originalErr = System.err
            def baos = new ByteArrayOutputStream()
            System.setErr(new PrintStream(baos, true))

        and: 'an exception whose stack trace mixes application and internal frames'
            def exception = new RuntimeException('boom')
            exception.stackTrace = [
                new StackTraceElement('test.FooController', 'show', 'FooController.groovy', 6),
                new StackTraceElement('java.lang.reflect.Method', 'invoke', 'Method.java', 580)
            ] as StackTraceElement[]

        when: 'the exception is filtered'
            filterer.filter(exception)

        then: "a 'Full Stack Trace:' entry is emitted by the filterer for backwards compatibility"
            System.err.flush()
            baos.toString().contains('Full Stack Trace:')

        cleanup:
            System.setErr(originalErr)
    }

    def 'filter does not emit a StackTrace log entry when logFullStackTraceOnFilter is disabled'() {
        given: 'captured System.err'
            def originalErr = System.err
            def baos = new ByteArrayOutputStream()
            System.setErr(new PrintStream(baos, true))

        and: 'a filterer with the side-effect emission disabled'
            def quietFilterer = new DefaultStackTraceFilterer()
            quietFilterer.logFullStackTraceOnFilter = false

        and: 'an exception whose stack trace mixes application and internal frames'
            def exception = new RuntimeException('boom')
            exception.stackTrace = [
                new StackTraceElement('test.FooController', 'show', 'FooController.groovy', 6),
                new StackTraceElement('java.lang.reflect.Method', 'invoke', 'Method.java', 580)
            ] as StackTraceElement[]

        when: 'the exception is filtered'
            quietFilterer.filter(exception)

        then: "no 'Full Stack Trace:' entry is emitted by the filterer"
            System.err.flush()
            !baos.toString().contains('Full Stack Trace:')

        cleanup:
            System.setErr(originalErr)
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

    def 'filter emits one StackTrace log entry per throwable when walking the cause chain by default'() {
        given: 'captured System.err'
            def originalErr = System.err
            def baos = new ByteArrayOutputStream()
            System.setErr(new PrintStream(baos, true))

        and: 'a wrapped exception whose wrapper and cause mix application and internal frames'
            def rootCause = new IllegalStateException('root cause')
            rootCause.stackTrace = [
                new StackTraceElement('test.FooService', 'doStuff', 'FooService.groovy', 3),
                new StackTraceElement('java.lang.reflect.Method', 'invoke', 'Method.java', 580)
            ] as StackTraceElement[]

            def exception = new RuntimeException('boom', rootCause)
            exception.stackTrace = [
                new StackTraceElement('test.FooController', 'show', 'FooController.groovy', 6),
                new StackTraceElement('java.lang.reflect.Method', 'invoke', 'Method.java', 580)
            ] as StackTraceElement[]

        when: 'recursive filtering is applied to the top-level exception'
            filterer.filter(exception, true)

        then: "a 'Full Stack Trace:' entry is emitted per throwable in the chain (pre-7.1 behaviour)"
            System.err.flush()
            baos.toString().count('Full Stack Trace:') == 2

        cleanup:
            System.setErr(originalErr)
    }

    def 'filter does not emit a StackTrace log entry when walking the cause chain with logFullStackTraceOnFilter disabled'() {
        given: 'captured System.err'
            def originalErr = System.err
            def baos = new ByteArrayOutputStream()
            System.setErr(new PrintStream(baos, true))

        and: 'a filterer with the side-effect emission disabled'
            def quietFilterer = new DefaultStackTraceFilterer()
            quietFilterer.logFullStackTraceOnFilter = false

        and: 'a wrapped exception whose wrapper and cause mix application and internal frames'
            def rootCause = new IllegalStateException('root cause')
            rootCause.stackTrace = [
                new StackTraceElement('test.FooService', 'doStuff', 'FooService.groovy', 3),
                new StackTraceElement('java.lang.reflect.Method', 'invoke', 'Method.java', 580)
            ] as StackTraceElement[]

            def exception = new RuntimeException('boom', rootCause)
            exception.stackTrace = [
                new StackTraceElement('test.FooController', 'show', 'FooController.groovy', 6),
                new StackTraceElement('java.lang.reflect.Method', 'invoke', 'Method.java', 580)
            ] as StackTraceElement[]

        when: 'recursive filtering is applied to the top-level exception'
            quietFilterer.filter(exception, true)

        then: "no 'Full Stack Trace:' entry is emitted for any throwable in the chain"
            System.err.flush()
            !baos.toString().contains('Full Stack Trace:')

        cleanup:
            System.setErr(originalErr)
    }

    def 'recursive filtering visits every throwable in the cause chain and sanitizes each'() {
        given: 'a cause chain with both application and internal stack frames'
            def filterer = new CountingStackTraceFilterer()
            def rootCause = new IllegalStateException('root cause')
            rootCause.stackTrace = [
                new StackTraceElement('test.FooService', 'doStuff', 'FooService.groovy', 3),
                new StackTraceElement('org.codehaus.groovy.runtime.InvokerHelper', 'invokeMethod', 'InvokerHelper.java', 12)
            ] as StackTraceElement[]

            def wrappedCause = new RuntimeException('wrapped cause', rootCause)
            wrappedCause.stackTrace = [
                new StackTraceElement('test.FooController', 'display', 'FooController.groovy', 11),
                new StackTraceElement('org.codehaus.groovy.runtime.callsite.CallSiteArray', 'defaultCall', 'CallSiteArray.java', 15)
            ] as StackTraceElement[]

            def exception = new RuntimeException('top level', wrappedCause)
            exception.stackTrace = [
                new StackTraceElement('test.FooController', 'show', 'FooController.groovy', 7),
                new StackTraceElement('org.codehaus.groovy.runtime.ScriptBytecodeAdapter', 'unwrap', 'ScriptBytecodeAdapter.java', 20)
            ] as StackTraceElement[]

        when: 'recursive filtering is applied to the top-level exception'
            filterer.filter(exception, true)

        then: 'filter is invoked once per throwable in the cause chain, in cause-chain order'
            filterer.singleExceptionFilterInvocations == 3
            filterer.filteredSources == [exception, wrappedCause, rootCause]

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
