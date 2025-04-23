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

package org.grails.compiler.injection

import groovy.transform.Generated
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.classgen.GeneratorContext
import org.codehaus.groovy.control.SourceUnit
import org.grails.compiler.injection.ASTValidationErrorsHelper
import grails.compiler.ast.ClassInjector
import org.grails.compiler.injection.GrailsAwareClassLoader
import org.springframework.validation.Errors

import spock.lang.Specification

import java.lang.reflect.Method

class ASTValidationErrorsHelperSpec extends Specification {

    static gcl

    void setupSpec() {
        gcl = new GrailsAwareClassLoader()
        def transformer = new ClassInjector() {
            @Override
            void performInjection(SourceUnit source, ClassNode classNode) {
                performInject(source, null, classNode)
            }

            @Override
            void performInjectionOnAnnotatedClass(SourceUnit source, ClassNode classNode) {
                //To change body of implemented methods use File | Settings | File Templates.
            }

            @Override
            void performInjection(SourceUnit source, GeneratorContext context, ClassNode classNode) {
                new ASTValidationErrorsHelper().injectErrorsCode(classNode)
            }
            @Override
            boolean shouldInject(URL url) { true }
        }
        gcl.classInjectors = [transformer] as ClassInjector[]
    }

    void 'Test injected errors property'() {
        given:
            def widgetClass = gcl.parseClass('class MyWidget{}')

        when:
            def widget = widgetClass.newInstance()

        then:
            !widget.hasErrors()

        when:
            widget.setErrors([hasErrors: { false }] as Errors)

        then:
            !widget.hasErrors()

        when:
            widget.setErrors([hasErrors: { true }] as Errors)

        then:
            widget.hasErrors()

        when:
            widget.clearErrors()

        then:
            !widget.hasErrors()

        when:
            def localErrors = [:] as Errors
            widget.setErrors(localErrors)

        then:
            localErrors.is(widget.getErrors())
    }

    void 'Test injected errors property methods are marked with Generated annotation'() {
        given:
        def widgetClass = gcl.parseClass('class MyWidget{}')

        and: 'injected method names to it'
        List<String> injectedMethodNames = [
            "setErrors",
            "getErrors",
            "hasErrors",
            "clearErrors",
            "initErrors"
        ]

        expect: 'injected methods marked as Generated'
        widgetClass.getMethods().each { Method widgetMethod ->
            if (widgetMethod.name in injectedMethodNames) {
                assert widgetMethod.isAnnotationPresent(Generated)
            }
        }
    }
}
