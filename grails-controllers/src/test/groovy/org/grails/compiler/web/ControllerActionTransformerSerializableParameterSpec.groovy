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
import grails.web.servlet.context.GrailsWebApplicationContext
import org.codehaus.groovy.control.CompilationUnit
import org.springframework.web.context.WebApplicationContext
import org.springframework.web.context.request.RequestContextHolder

import spock.lang.Specification

import org.grails.compiler.injection.GrailsAwareClassLoader

/**
 * Verifies that an action parameter declared {@code Serializable} is bound from the request the same
 * way a {@code String} one is.
 *
 * <p>{@code Serializable} is the type a domain class identifier is declared as when the action does not
 * know the type itself - {@code Long} under Hibernate, {@code String} under MongoDB - and it is what
 * {@code GormEntity.get(Serializable)} accepts. It used to be treated as a command object type, and
 * being an interface it could not be one, so the parameter was reported and then bound to
 * {@code null}.</p>
 */
class ControllerActionTransformerSerializableParameterSpec extends Specification {

    GrailsAwareClassLoader gcl

    void setup() {
        System.properties[BuildSettings.CONVERT_CLOSURES_KEY] = 'true'
        gcl = new GrailsAwareClassLoader()
        ControllerActionTransformer transformer = new ControllerActionTransformer() {
            @Override
            boolean shouldInject(URL url) {
                true
            }
        }
        transformer.setCompilationUnit(new CompilationUnit())
        gcl.classInjectors = [transformer] as ClassInjector[]
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()
        def appCtx = new GrailsWebApplicationContext()
        webRequest.servletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, appCtx)
    }

    void cleanup() {
        RequestContextHolder.resetRequestAttributes()
        System.properties[BuildSettings.CONVERT_CLOSURES_KEY] = 'false'
    }

    void 'a Serializable parameter is bound from the request parameter of the same name'() {
        given:
        def controller = serializableIdController()

        when:
        controller.params.id = '42'

        then:
        controller.show().value == '42'
    }

    void 'a Serializable parameter is null where the request does not carry it'() {
        given:
        def controller = serializableIdController()

        expect:
        controller.show().value == null
    }

    void 'a Serializable parameter honours @RequestParameter'() {
        given:
        def cls = gcl.parseClass('''
            import grails.web.RequestParameter

            @grails.artefact.Artefact('Controller')
            class RenamedIdController {
                def show(@RequestParameter('personId') Serializable id) {
                    [value: id]
                }
            }
        ''')
        def controller = cls.getDeclaredConstructor().newInstance()

        when:
        controller.params['personId'] = 'abc'

        then:
        controller.show().value == 'abc'
    }

    void 'an action taking a Serializable parameter still generates the no-argument entry point'() {
        given:
        def cls = serializableIdController().getClass()

        expect:
        cls.getMethod('show')
        cls.getMethod('show', Serializable)
    }

    void 'a command object that implements Serializable is still bound as a command object'() {
        when: 'the command object is the declared type, not Serializable itself'
        def cls = gcl.parseClass('''
            @grails.artefact.Artefact('Controller')
            class WidgetController {
                def save(WidgetCommand widget) {
                    [name: widget?.name]
                }

                def $newCommand() {
                    new WidgetCommand(name: 'Sally')
                }
            }

            class WidgetCommand implements Serializable {
                String name
            }
        ''')
        def controller = cls.getDeclaredConstructor().newInstance()
        def command = controller.$newCommand()

        then: 'it went down the command object path, which is what makes it validateable'
        command.validate()
        command.name == 'Sally'
    }

    void 'a Serializable parameter compiles under static compilation'() {
        when:
        def cls = gcl.parseClass('''
            @grails.artefact.Artefact('Controller')
            @groovy.transform.CompileStatic
            class StaticIdController {
                Map show(Serializable id) {
                    [value: id]
                }
            }
        ''')
        def controller = cls.getDeclaredConstructor().newInstance()
        controller.params.id = '7'

        then:
        controller.show().value == '7'
    }

    private serializableIdController() {
        gcl.parseClass('''
            @grails.artefact.Artefact('Controller')
            class SerializableIdController {
                def show(Serializable id) {
                    [value: id]
                }
            }
        ''').getDeclaredConstructor().newInstance()
    }
}
