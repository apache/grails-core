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

package org.grails.web.controllers

import org.codehaus.groovy.control.MultipleCompilationErrorsException
import grails.compiler.ast.ClassInjector
import org.grails.compiler.injection.GrailsAwareClassLoader
import org.grails.compiler.web.ControllerActionTransformer

import spock.lang.Specification

class ControllerExceptionHandlerCompilationErrorsSpec extends Specification {

    static gcl

    void setupSpec() {
        gcl = new GrailsAwareClassLoader()
        def transformer = new ControllerActionTransformer() {
            @Override
            boolean shouldInject(URL url) { true }
        }
        gcl.classInjectors = [transformer]as ClassInjector[]
    }

    void 'Test multiple exception handlers for the same exception type'() {
        when: 'Two handlers exist for the same exception type'
            gcl.parseClass('''
            class TestController {
                def methodOne(NumberFormatException e){}
                def methodTwo(NumberFormatException e){}
            }
''')
        then: 'compilation fails'
            MultipleCompilationErrorsException e = thrown()
            e.message.contains 'A controller may not define more than 1 exception handler for a particular exception type.  [TestController] defines the [methodOne] and [methodTwo] exception handlers which each accept a [java.lang.NumberFormatException] which is not allowed.'
    }
}
