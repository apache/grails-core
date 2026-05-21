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

package org.grails.compiler.web

import grails.compiler.ast.ClassInjector

import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.grails.compiler.injection.GrailsAwareClassLoader
import org.grails.compiler.web.ControllerActionTransformer

import spock.lang.Specification

class ControllerActionTransformerCompilationErrorsSpec extends Specification {

    static gcl

    void setupSpec() {
        gcl = new GrailsAwareClassLoader()
        def transformer = new ControllerActionTransformer() {
            @Override
            boolean shouldInject(URL url) { true }
        }
        gcl.classInjectors = [transformer]as ClassInjector[]
    }

    void 'Test overloaded method actions'() {
        when: 'A controller overloads a method action'
            gcl.parseClass('''
            class TestController {
                def methodAction(String s){}
                def methodAction(Integer i){}
            }
''')
        then:
            MultipleCompilationErrorsException e = thrown()
            e.message.contains 'Controller actions may not be overloaded.  The [methodAction] action has been overloaded in [TestController].'
    }

    void "Test default parameter values"() {
        when: 'A method action has default parameter values'
            gcl.parseClass('''
            class TestController {
                def methodAction(int i = 42){}
            }
            ''')

            then:
            MultipleCompilationErrorsException e = thrown()
            e.message.contains 'Parameter [i] to method [methodAction] has default value [42].  Default parameter values are not allowed in controller action methods.'
    }
}
