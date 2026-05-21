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

class StackTraceFiltererSpec extends Specification {

    private filterer = new DefaultStackTraceFilterer()
    private gcl = new GroovyClassLoader()

    void "Test basic filter"() {
        given: "A controller that should throw a MissingPropertyException"
            def cls = gcl.parseClass('''
package test

class FooController {
    def show = {
        display()
    }

    void display() {
        notHere
    }
}
''')

        when: "The stack trace is filtered with custom packages"
           filterer.setCutOffPackage("org.spockframework.util")
           Throwable exception
           try {
               cls.getDeclaredConstructor().newInstance().show()
           } catch (e) {
               filterer.filter(e)
               exception = e
           }

        then: "Only valid stack elements are retained"
            exception != null

        when:
        StackTraceElement[] stackTraces = exception.stackTrace

        then:
        stackTraces.find { it.className == 'test.FooController' && it.lineNumber == 10 }
        stackTraces.find { it.className.startsWith('test.FooController') && it.lineNumber == 6 }
    }

    void "Test deep filter"() {
        given: "A controller that calls a service and rethrows an exception"
            def cls = gcl.parseClass('''
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
''')

        when: "The stack trace is filtered with custom packages"
           filterer.setCutOffPackage("org.spockframework.util")
           Throwable exception
           try {
               cls.getDeclaredConstructor().newInstance().show()
           } catch (e) {
               filterer.filter(e, true)
               println getExceptionContents(e)
               exception = e
           }

        then: "Only valid stack elements are retained"
            exception != null

        when:
        StackTraceElement[] stackTraces = exception.stackTrace

        then:
        stackTraces.find { it.className == 'test.FooController' && it.lineNumber == 15 }
        stackTraces.find { it.className.startsWith('test.FooController') && it.lineNumber == 7 }
    }

    private String getExceptionContents(Throwable e) {
        final sw = new StringWriter()
        def pw = new PrintWriter(sw)
        e.printStackTrace pw
        return sw.toString()
    }
}
