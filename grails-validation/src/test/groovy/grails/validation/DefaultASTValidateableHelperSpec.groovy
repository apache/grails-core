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

import grails.util.Holders
import groovy.transform.Generated
import org.grails.web.context.ServletEnvironmentGrailsApplicationDiscoveryStrategy
import org.springframework.web.context.support.GenericWebApplicationContext
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.classgen.GeneratorContext
import org.codehaus.groovy.control.SourceUnit

import grails.compiler.ast.ClassInjector

import org.grails.compiler.injection.GrailsAwareClassLoader
import org.springframework.mock.web.MockServletContext
import org.springframework.web.context.WebApplicationContext

import spock.lang.Specification

import java.lang.reflect.Method

/**
 * Tests relevant to grails.validation.Validateable
 */
class DefaultASTValidateableHelperSpec extends Specification {

    static widgetClass

    def setupSpec() {
        def gcl = new GrailsAwareClassLoader()
        def transformer = new ClassInjector() {
            void performInjection(SourceUnit source, ClassNode classNode) {
                performInjection(source, null, classNode)
            }

            @Override
            void performInjectionOnAnnotatedClass(SourceUnit source, ClassNode classNode) {
                //To change body of implemented methods use File | Settings | File Templates.
            }

            void performInjection(SourceUnit source, GeneratorContext context, ClassNode classNode) {
                new DefaultASTValidateableHelper().injectValidateableCode(classNode, false)
            }
            boolean shouldInject(URL url) { true }
        }
        gcl.classInjectors = [transformer]as ClassInjector[]
        widgetClass = gcl.parseClass('''
        class Widget {
            String name
            String category
            Integer count

            static constraints = {
                name matches: /[A-Z].*/
                category size: 3..50
                count range: 1..10
            }
        }
        ''')

        def servletContext = new MockServletContext()
        def applicationContext = new GenericWebApplicationContext(servletContext)
        applicationContext.refresh()
        servletContext.setAttribute WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, applicationContext

        Holders.servletContext = servletContext
        Holders.addApplicationDiscoveryStrategy(new ServletEnvironmentGrailsApplicationDiscoveryStrategy(servletContext));
    }

    def cleanupSpec() {
        Holders.clear()
    }

    void 'Test constraints property'() {
        when:
            def constraints = widgetClass.constraints

        then:
            constraints.name.matches == /[A-Z].*/
            constraints.category.size == 3..50
            constraints.count.range == 1..10
    }

    void 'Test constraints getter method is marked as Generated'() {
        expect:
        widgetClass.getMethod('getConstraints').isAnnotationPresent(Generated)
    }

    void 'Test validate method returns has a declared return type of boolean, not Boolean'() {
        when:
            def validateListArgMethod = widgetClass.metaClass.methods.find {
                'validate' == it.name && it.paramsCount == 1 && it.parameterTypes[0].theClass == List
            }
            def validateNoArgMethod = widgetClass.metaClass.methods.find {
                'validate' == it.name && it.paramsCount == 0
            }

        then:
            Boolean.TYPE == validateListArgMethod.returnType
            Boolean.TYPE == validateNoArgMethod.returnType
    }

    void 'Test validate method on uninitialized object'() {
        given:
            def widget = widgetClass.newInstance()

        when:
            def isValid = widget.validate()

        then:
            !isValid
    }

    void 'Test validate method on invalid object'() {
        given:
            def widget = widgetClass.newInstance()
            widget.name = 'joe'
            widget.count = 42

        when:
            def isValid = widget.validate()
            def errorCount = widget.errors.errorCount

        then:
            !isValid
            3 == errorCount
    }

    void 'Test validate method is marked as Generated'() {
        when:
        Method validateListArgMethod = widgetClass.getMethods().find { Method it ->
            'validate' == it.name && it.parameterTypes.length == 1
        }
        Method validateNoArgMethod = widgetClass.getMethods().find { Method it ->
            'validate' == it.name && it.parameterTypes.length == 0
        }

        then:
        validateListArgMethod.isAnnotationPresent(Generated)
        validateNoArgMethod.isAnnotationPresent(Generated)
    }

    void 'Test clearErrors'() {
        given:
            def widget = widgetClass.newInstance()
            widget.name = 'joe'
            widget.count = 42

        when:
            def isValid = widget.validate()
            def errorCount = widget.errors.errorCount

        then:
            !isValid
            3 == errorCount

        when:
            widget.clearErrors()
            errorCount = widget.errors.errorCount

        then:
            0 == errorCount

        when:
            isValid = widget.validate()
            errorCount = widget.errors.errorCount

        then:
            !isValid
            3 == errorCount
    }

    void 'Test revalidating invalid object'() {
        given:
            def widget = widgetClass.newInstance()
            widget.name = 'joe'
            widget.count = 42

        when:
            def isValid = widget.validate()
            def errorCount = widget.errors.errorCount

        then:
            !isValid
            3 == errorCount

        when:
            isValid = widget.validate()
            errorCount = widget.errors.errorCount

        then:
            !isValid
            3 == errorCount
    }

    void 'Test revalidating object after fixing errors'() {
        given:
            def widget = widgetClass.newInstance()
            widget.name = 'joe'
            widget.count = 42

        when:
            def isValid = widget.validate()
            def errorCount = widget.errors.errorCount

        then:
            !isValid
            3 == errorCount

        when:
            widget.name = 'Joe'
            widget.count = 9
            widget.category = 'Playa'
            isValid = widget.validate()
            errorCount = widget.errors.errorCount

        then:
            isValid
            0 == errorCount
    }

    void 'Test validate method on valid object'() {
        given:
            def widget = widgetClass.newInstance()
            widget.name = 'Joe'
            widget.count = 9
            widget.category = 'Playa'

        when:
            def isValid = widget.validate()
            def errorCount = widget.errors.errorCount

        then:
            isValid
            0 == errorCount
    }

    void 'Test validate method with a List argument'() {
        given:
            def widget = widgetClass.newInstance()
            widget.name = 'joe'
            widget.count = 42

        when:
            def isValid = widget.validate(['count', 'category'])
            def errorCount = widget.errors.errorCount
            def countError = widget.errors.getFieldError('count')
            def nameError = widget.errors.getFieldError('name')
            def categoryError = widget.errors.getFieldError('category')

        then:
            !isValid
            2 == errorCount
            countError
            categoryError
            !nameError
    }

    void 'Test validate method with an empty List argument'() {
        given:
            def widget = widgetClass.newInstance()
            widget.name = 'joe'
            widget.count = 42

        when:
            def isValid = widget.validate([])
            def errorCount = widget.errors.errorCount

        then:
            isValid
            0 == errorCount
    }

    void 'Test validate method on an object that has had values rejected with an ObjectError'() {
        given:
            def widget = widgetClass.newInstance()
            widget.name = 'Joe'
            widget.count = 2
            widget.category = 'some category'

        when:
            widget.errors.reject 'count'
            def isValid = widget.validate()
            def errorCount = widget.errors.errorCount

        then:
            !isValid
            1 == errorCount
    }
}
