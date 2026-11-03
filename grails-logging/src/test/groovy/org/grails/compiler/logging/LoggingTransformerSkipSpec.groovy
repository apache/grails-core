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
package org.grails.compiler.logging

import java.lang.reflect.Field
import java.lang.reflect.Modifier

import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.slf4j.Logger
import spock.lang.Specification

import grails.compiler.ast.ClassInjector
import org.grails.compiler.injection.GrailsAwareClassLoader

class LoggingTransformerSkipSpec extends Specification {

    LoggingTransformer transformer = new LoggingTransformer()
    GrailsAwareClassLoader gcl = new GrailsAwareClassLoader()

    void setup() {
        gcl.classInjectors = [transformer] as ClassInjector[]
    }

    void 'the transformer injects into every artefact url'() {
        expect:
        transformer.shouldInject(new URL('file:/app/grails-app/controllers/FooController.groovy'))
        transformer.shouldInject(null)
    }

    void 'a class already annotated with a groovy logging annotation is left alone'() {
        when:
        Class cls = gcl.parseClass('''
@groovy.util.logging.Log
class LoggingController {
    def index() { log }
}
''', 'foo/grails-app/controllers/LoggingController.groovy')
        Field field = cls.getDeclaredField('log')

        then:
        field.type == java.util.logging.Logger
        cls.getDeclaredConstructor().newInstance().index() instanceof java.util.logging.Logger
    }

    void 'a private log field is still injected and rejected by the log transform'() {
        when:
        gcl.parseClass('''
class LoggingController {
    private log = 'notALogger'
    def index() { log }
}
''', 'foo/grails-app/controllers/LoggingController.groovy')

        then:
        MultipleCompilationErrorsException e = thrown()
        e.message.contains('Class annotated with Log annotation cannot have log field declared')
    }

    void 'a non private log field is not replaced'() {
        when:
        Class cls = gcl.parseClass('''
class LoggingController {
    protected log = 'notALogger'
    def index() { log }
}
''', 'foo/grails-app/controllers/LoggingController.groovy')

        then:
        cls.getDeclaredField('log').type == Object
        cls.getDeclaredConstructor().newInstance().index() == 'notALogger'
    }

    void 'the transformer is idempotent for a class node it already visited'() {
        given:
        Class cls = gcl.parseClass('''
class LoggingController {
    def index() { log }
}
''', 'foo/grails-app/controllers/LoggingController.groovy')

        expect:
        cls.getDeclaredFields().count { it.name == 'log' } == 1
        cls.getDeclaredConstructor().newInstance().index() instanceof Logger
    }

}
