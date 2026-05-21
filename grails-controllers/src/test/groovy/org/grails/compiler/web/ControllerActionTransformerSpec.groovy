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
import grails.util.BuildSettings
import grails.util.GrailsWebMockUtil
import grails.web.Action
import grails.web.servlet.context.GrailsWebApplicationContext
import groovy.transform.Generated
import org.codehaus.groovy.control.CompilationUnit
import spock.lang.Ignore

import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.lang.reflect.Modifier

import org.grails.compiler.injection.GrailsAwareClassLoader
import org.springframework.web.context.WebApplicationContext
import org.springframework.web.context.request.RequestContextHolder

import spock.lang.Specification

class ControllerActionTransformerSpec extends Specification {

    def gcl

    void setup() {
        System.properties[BuildSettings.CONVERT_CLOSURES_KEY] = 'true'
        gcl = new GrailsAwareClassLoader()
        def transformer = new ControllerActionTransformer() {
            @Override
            boolean shouldInject(URL url) {
                true
            }
        }
        transformer.setCompilationUnit(new CompilationUnit())
        gcl.classInjectors = [transformer] as ClassInjector[]
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()
        def appCtx = new GrailsWebApplicationContext()
        def servletContext = webRequest.servletContext
        servletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, appCtx)
    }

    void "Test that a closure action has changed to method"() {

        when:
            def cls = gcl.parseClass('''
            @grails.artefact.Artefact('Controller')
            class TestTransformedToController {

                def action = {
                }

                }
            ''')
            def controller = cls.newInstance()

        then:
          controller
          controller.getClass().getMethod("action", [] as Class[]) != null

        and: 'its not marked as Generated'
            controller.getClass().getMethod("action", [] as Class[]).isAnnotationPresent(Generated)
    }

    void 'Test that user applied annotations are applied to generated action methods'() {
        given:
        def cls = gcl.parseClass('''
        @grails.artefact.Artefact('Controller')
        class SomeController {
            @Deprecated
            def action1(){}
            @Deprecated
            def action2(String paramName){}
}
''')

        when:
        def action1NoArgMethod = cls.getMethod('action1')

        then:
        action1NoArgMethod.getAnnotation(Action)
        action1NoArgMethod.getAnnotation(Deprecated)

        when:
        def action2MethodWithStringArg = cls.getMethod('action2', [String] as Class[])

        then:
        !action2MethodWithStringArg.getAnnotation(Action)
        action2MethodWithStringArg.getAnnotation(Deprecated)

        when:
        def action2NoArgMethod = cls.getMethod('action2')

        then:
        action2NoArgMethod.getAnnotation(Action)
        action2NoArgMethod.getAnnotation(Deprecated)
    }
    
    void 'Test that a controller may have an abstract method - GRAILS-10509'() {
        given:
        def controllerClass = gcl.parseClass('''
            @grails.artefact.Artefact('Controller')
            abstract class SomeController {
                def someAction() {}
                abstract someAbstractMethod()
            }
''')
        when:
        def method = controllerClass.getMethod('someAbstractMethod')
        
        then:
        Modifier.isAbstract(method.modifiers)
        
        when:
        method = controllerClass.getMethod('someAction')
        
        then:
        !Modifier.isAbstract(method.modifiers)
    }

    void 'Test action overiding'() {
        given:
            def superControllerClass = gcl.parseClass('''
            @grails.artefact.Artefact('Controller')
            class SuperController {
                def methodAction() {
                    [ actionInvoked: 'SuperController.methodAction' ]
                }
                def methodActionWithParam(String s) {
                    [ paramValue: s ]
                }
            }
''')
            def superController = superControllerClass.newInstance()
            def subControllerClass = gcl.parseClass('''
            class SubController extends SuperController {
                def methodAction() {
                    [ actionInvoked: 'SubController.methodAction' ]
                }
                def methodActionWithParam(Integer i) {
                    [ paramValue: i ]
                }
            }
''')
            def subController = subControllerClass.newInstance()

        when:
            def model = superController.methodAction()

        then:
            'SuperController.methodAction' == model.actionInvoked

        when:
            superController.params.s = 'Super Controller Param'
            model = superController.methodActionWithParam()

        then:
            'Super Controller Param' == model.paramValue

        when:
            model = subController.methodAction()

        then:
            'SubController.methodAction' == model.actionInvoked

        when:
            subController.params.s = 'Super Controller Param'
            model = subController.methodActionWithParam()

        then:
            null == model.paramValue

        when:
            subController.params.i = 42
            model = subController.methodActionWithParam()

        then:
            42 == model.paramValue
    }


    void "test controller with trait action with command params"() {
        given:
        def cls = gcl.parseClass('''
            @grails.artefact.Artefact('Controller')
            class TestTraitActionToController implements ShowMethod {


            }
            class MyCommandWithArg implements grails.validation.Validateable {

            }
            trait ShowMethod {

                @grails.web.Action
                def show(MyCommandWithArg myCommandWithArg) {
                    !myCommandWithArg.hasErrors()
                }

            }
            ''')
        def controller = cls.newInstance()

        when:
        Boolean valid = controller.show()

        then:
        valid
    }


    void "Test command object gets Validateable injected"() {

        when:
        def cls = gcl.parseClass('''
            @grails.artefact.Artefact('Controller')
            class TestMyCommandObjController {

                def action(MyCommand myCommand) {
                }

                def $test() {
                    new MyCommand(name: "Sally")
                }

            }

            class MyCommand {
                String name
            }
            ''')
        def controller = cls.newInstance()
        def myCommand = controller.$test()

        then:
        controller
        myCommand
        myCommand.validate()
    }

    void "Test command object injected constructor will be marked as Generated"() {

        when:
        def cls = gcl.parseClass('''
            @grails.artefact.Artefact('Controller')
            class TestMyCommandObjController {
                def action(MyCommand myCommand) {
                }

                def $test() {
                    new MyCommand(name: "Sally")
                }

            }

            class MyCommand {
                String name
            }
            ''')
        def controller = cls.newInstance()
        def myCommand = controller.$test()

        then:
        myCommand.getClass().getConstructors().each { Constructor constructor ->
            assert constructor.isAnnotationPresent(Generated)
        }
    }

    @Ignore("This is flaky test")
    void "Test Controller Action transformer marks its new methods as Generated"() {

        when:
        def cls = gcl.parseClass('''
            @grails.artefact.Artefact('Controller')
            class MyCommandObjTestController {
                def action(MyCommand myCommand) {
                }
                
                def actionAsClos = { MyCommand myCommand ->
                }
                
                def $test() {
                    new MyCommand(name: "Sally")
                }
            }

            class MyCommand {
                String name
            }
            ''')
        def controller = cls.newInstance()
        def myCommand = controller.$test()

        then:
        Method actionMethodWithoutCommand = controller.getClass().getMethod('action')
        actionMethodWithoutCommand.isAnnotationPresent(Generated)

        Method actionMethodWitCommand = controller.getClass().getMethod('action', myCommand.class)
        !actionMethodWitCommand.isAnnotationPresent(Generated)

        Method actionAsClosureMethodWithoutCommand = controller.getClass().getMethod('actionAsClos')
        actionAsClosureMethodWithoutCommand.isAnnotationPresent(Generated)

        Method actionAsClosureMethodWitCommand = controller.getClass().getMethod('actionAsClos', (Class) myCommand.class)
        actionAsClosureMethodWitCommand.isAnnotationPresent(Generated)
    }

    def cleanup() {
        RequestContextHolder.resetRequestAttributes()
        System.properties[BuildSettings.CONVERT_CLOSURES_KEY] = 'false'
    }
}


