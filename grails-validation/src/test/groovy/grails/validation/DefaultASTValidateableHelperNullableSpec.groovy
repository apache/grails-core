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
package grails.validation

import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.classgen.GeneratorContext
import org.codehaus.groovy.control.SourceUnit
import org.springframework.mock.web.MockServletContext
import org.springframework.web.context.WebApplicationContext
import org.springframework.web.context.support.GenericWebApplicationContext
import spock.lang.Shared
import spock.lang.Specification

import grails.compiler.ast.ClassInjector
import grails.util.Holders
import org.grails.compiler.injection.GrailsAwareClassLoader
import org.grails.validation.ConstraintEvalUtils

class DefaultASTValidateableHelperNullableSpec extends Specification {

    private static final String SOURCE = '''
        class Base {
            String inherited
        }
        class Gadget extends Base {
            String name
            Integer count
            static String ignoredStatic
            String getComputed() { 'c' }
            String getWithArg(String a) { a }
            static constraints = {
                name size: 1..5
            }
        }
    '''

    @Shared
    Class nullableByDefault

    @Shared
    Class notNullableByDefault

    void setupSpec() {
        nullableByDefault = compile(true)
        notNullableByDefault = compile(false)
        MockServletContext servletContext = new MockServletContext()
        GenericWebApplicationContext applicationContext = new GenericWebApplicationContext(servletContext)
        applicationContext.refresh()
        servletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, applicationContext)
        Holders.servletContext = servletContext
    }

    void cleanupSpec() {
        Holders.clear()
    }

    void setup() {
        ConstraintEvalUtils.clearDefaultConstraints()
    }

    private static Class compile(boolean defaultNullable) {
        GrailsAwareClassLoader gcl = new GrailsAwareClassLoader()
        ClassInjector injector = new ClassInjector() {
            void performInjection(SourceUnit source, ClassNode classNode) {
                performInjection(source, null, classNode)
            }

            void performInjectionOnAnnotatedClass(SourceUnit source, ClassNode classNode) {
            }

            void performInjection(SourceUnit source, GeneratorContext context, ClassNode classNode) {
                if (classNode.nameWithoutPackage == 'Gadget') {
                    new DefaultASTValidateableHelper().injectValidateableCode(classNode, defaultNullable)
                }
            }

            boolean shouldInject(URL url) { true }
        }
        gcl.classInjectors = [injector] as ClassInjector[]
        gcl.parseClass(SOURCE)
        gcl.loadClass('Gadget')
    }

    void 'properties without explicit constraints are made non nullable only when nullable is not the default'() {
        when:
        Map strict = notNullableByDefault.constraints
        Map lenient = nullableByDefault.constraints

        then:
        strict.keySet() == ['name', 'count', 'inherited', 'computed'] as Set
        !strict.name.nullable
        !strict.count.nullable
        !strict.inherited.nullable
        !strict.computed.nullable
        strict.name.size == (1..5)
        lenient.keySet() == ['name'] as Set
        lenient.name.nullable
        notNullableByDefault.constraints.is(strict)
    }

    void 'the injected validate methods report errors for the missing values'() {
        given:
        Object strict = notNullableByDefault.getDeclaredConstructor().newInstance()
        Object lenient = nullableByDefault.getDeclaredConstructor().newInstance()

        expect:
        !strict.validate()
        strict.errors.errorCount == 3
        strict.errors.getFieldError('name').code == 'nullable'
        strict.errors.getFieldError('count').code == 'nullable'
        strict.errors.getFieldError('inherited').code == 'nullable'
        strict.validate(['computed'])
        lenient.validate()
        !lenient.errors.hasErrors()
    }

}
