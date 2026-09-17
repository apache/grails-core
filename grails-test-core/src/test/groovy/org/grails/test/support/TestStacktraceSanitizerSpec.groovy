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
package org.grails.test.support

import spock.lang.Specification

class TestStacktraceSanitizerSpec extends Specification {

    void 'the sanitizer truncates the trace at the grails test runner and filters framework frames'() {
        given:
        RuntimeException error = new RuntimeException('boom')
        error.stackTrace = [
                new StackTraceElement('com.example.MySpec', 'feature', 'MySpec.groovy', 10),
                new StackTraceElement('org.codehaus.groovy.runtime.callsite.CallSiteArray', 'call', 'CallSiteArray.java', 20),
                new StackTraceElement('com.example.Helper', 'run', 'Helper.groovy', 30),
                new StackTraceElement('_GrailsTest_groovy', 'run', '_GrailsTest.groovy', 40),
                new StackTraceElement('com.example.After', 'later', 'After.groovy', 50)
        ] as StackTraceElement[]

        when:
        Throwable result = TestStacktraceSanitizer.sanitize(error)

        then:
        result.is(error)
        result.stackTrace*.className == ['com.example.MySpec', 'com.example.Helper']
    }

    void 'a trace without the runner marker keeps every application frame'() {
        given:
        RuntimeException error = new RuntimeException('boom')
        error.stackTrace = [new StackTraceElement('com.example.MySpec', 'feature', 'MySpec.groovy', 10)] as StackTraceElement[]

        expect:
        TestStacktraceSanitizer.sanitize(error).stackTrace*.className == ['com.example.MySpec']
    }

}
