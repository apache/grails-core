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

    void 'Test secureBindData requires explicit allowed params'() {
        when: 'A controller calls secureBindData without an allowed params list'
            gcl.parseClass('''
            class TestController {
                def save() {
                    def target = new Person()
                    secureBindData(target, params)
                }
            }

            class Person {
                String name
            }
            ''')

        then:
            MultipleCompilationErrorsException e = thrown()
            e.message.contains 'secureBindData requires an explicit allowedParams list. Use secureBindData(target, params, allowedParams).'
    }

    void 'Test secureBindData requires explicit allowed params in controller trait'() {
        when: 'A controller trait calls secureBindData without an allowed params list'
            gcl.parseClass('''
            trait SecureBindingActions {
                def save() {
                    def target = new Person()
                    secureBindData(target, params)
                }
            }

            class TraitSecureBindingController implements SecureBindingActions {
            }

            class Person {
                String name
            }
            ''')

        then:
            MultipleCompilationErrorsException e = thrown()
            e.message.contains 'secureBindData requires an explicit allowedParams list. Use secureBindData(target, params, allowedParams).'
    }

    void 'Test secureBindData requires explicit allowed params in controller trait helper'() {
        when: 'A controller trait action delegates to a helper that calls secureBindData without an allowed params list'
            gcl.parseClass('''
            trait SecureBindingHelperActions {
                def save() {
                    bindWithoutAllowlist()
                }

                private bindWithoutAllowlist() {
                    def target = new Person()
                    secureBindData(target, params)
                }
            }

            class TraitHelperSecureBindingController implements SecureBindingHelperActions {
            }

            class Person {
                String name
            }
            ''')

        then:
            MultipleCompilationErrorsException e = thrown()
            e.message.contains 'secureBindData requires an explicit allowedParams list. Use secureBindData(target, params, allowedParams).'
    }

    void 'Test controller trait may declare a secureBindData helper method'() {
        when: 'A controller trait declares its own secureBindData helper method'
            gcl.parseClass('''
            trait HelperSecureBindingActions {
                def save() {
                    def target = new Person()
                    secureBindData(target, params)
                }

                def secureBindData(Object target, Object source) {
                    target.name = source.name
                }
            }

            class HelperTraitSecureBindingController implements HelperSecureBindingActions {
            }

            class Person {
                String name
            }
            ''')

        then:
            noExceptionThrown()
    }

    void 'Test secureBindData requires explicit allowed params in inherited controller action'() {
        when: 'A controller inherits an action that calls secureBindData without an allowed params list'
            gcl.parseClass('''
            class SecureBindingBase {
                def save() {
                    def target = new Person()
                    secureBindData(target, params)
                }
            }

            class InheritedSecureBindingController extends SecureBindingBase {
            }

            class Person {
                String name
            }
            ''')

        then:
            MultipleCompilationErrorsException e = thrown()
            e.message.contains 'secureBindData requires an explicit allowedParams list. Use secureBindData(target, params, allowedParams).'
    }

    void 'Test controller may declare a secureBindData helper method'() {
        when: 'A controller declares its own secureBindData helper method'
            gcl.parseClass('''
            class HelperSecureBindingController {
                def save() {
                    def target = new Person()
                    secureBindData(target, params)
                }

                private secureBindData(Object target, Object source) {
                    target.name = source.name
                }
            }

            class Person {
                String name
            }
            ''')

        then:
            noExceptionThrown()
    }

    void 'Test controller may inherit a secureBindData helper method'() {
        when: 'A controller inherits its own secureBindData helper method'
            gcl.parseClass('''
            class HelperSecureBindingBase {
                protected secureBindData(Object target, Object source) {
                    target.name = source.name
                }
            }

            class InheritedHelperSecureBindingController extends HelperSecureBindingBase {
                def save() {
                    def target = new Person()
                    secureBindData(target, params)
                }
            }

            class Person {
                String name
            }
            ''')

        then:
            noExceptionThrown()
    }

    void 'Test inherited action may call private secureBindData helper declared in same class'() {
        when: 'A superclass action calls its own private helper method'
            gcl.parseClass('''
            class PrivateActionHelperSecureBindingBase {
                def save() {
                    def target = new Person()
                    secureBindData(target, params)
                }

                private secureBindData(Object target, Object source) {
                    target.name = source.name
                }
            }

            class PrivateActionHelperSecureBindingController extends PrivateActionHelperSecureBindingBase {
            }

            class Person {
                String name
            }
            ''')

        then:
            noExceptionThrown()
    }

    void 'Test inherited action may call concrete controller secureBindData helper method'() {
        when: 'An inherited action calls a helper method declared by the concrete controller'
            gcl.parseClass('''
            class ConcreteHelperSecureBindingBase {
                def save() {
                    def target = new Person()
                    secureBindData(target, params)
                }
            }

            class ConcreteHelperSecureBindingController extends ConcreteHelperSecureBindingBase {
                protected secureBindData(Object target, Object source) {
                    target.name = source.name
                }
            }

            class Person {
                String name
            }
            ''')

        then:
            noExceptionThrown()
    }

    void 'Test controller may declare a secureBindData helper method with params argument'() {
        when: 'A helper can accept the dynamic params object as a Map argument'
            gcl.parseClass('''
            class SpecificHelperSecureBindingController {
                def save() {
                    def target = new Person()
                    secureBindData(target, params)
                }

                protected secureBindData(Object target, Map source) {
                    target.name = source.name
                }
            }

            class Person {
                String name
            }
            ''')

        then:
            noExceptionThrown()
    }

    void 'Test Object typed target with narrowed secureBindData helper still requires framework validation'() {
        when: 'A narrowed helper might not accept the runtime target value'
            gcl.parseClass('''
            class DynamicTargetHelperSecureBindingController {
                def save() {
                    Object target = new Person()
                    secureBindData(target, params)
                }

                protected secureBindData(Person target, Map source) {
                    target.name = source.name
                }
            }

            class Person {
                String name
            }
            ''')

        then:
            MultipleCompilationErrorsException e = thrown()
            e.message.contains 'secureBindData requires an explicit allowedParams list. Use secureBindData(target, params, allowedParams).'
    }

    void 'Test private inherited secureBindData helper does not suppress framework validation'() {
        when: 'A private inherited helper is not visible to the controller action call'
            gcl.parseClass('''
            class PrivateHelperSecureBindingBase {
                private secureBindData(Object target, Object source) {
                    target.name = source.name
                }
            }

            class PrivateInheritedHelperSecureBindingController extends PrivateHelperSecureBindingBase {
                def save() {
                    def target = new Person()
                    secureBindData(target, params)
                }
            }

            class Person {
                String name
            }
            ''')

        then:
            MultipleCompilationErrorsException e = thrown()
            e.message.contains 'secureBindData requires an explicit allowedParams list. Use secureBindData(target, params, allowedParams).'
    }

    void 'Test incompatible secureBindData helper signature does not suppress framework validation'() {
        when: 'A helper with the same arity cannot accept the controller action call arguments'
            gcl.parseClass('''
            class IncompatibleHelperSecureBindingController {
                def save() {
                    def target = new Person()
                    secureBindData(target, params)
                }

                private secureBindData(String target, String source) {
                    target + source
                }
            }

            class Person {
                String name
            }
            ''')

        then:
            MultipleCompilationErrorsException e = thrown()
            e.message.contains 'secureBindData requires an explicit allowedParams list. Use secureBindData(target, params, allowedParams).'
    }

    void 'Test secureBindData accepts explicit allowed params forms'() {
        when: 'A controller calls secureBindData with explicit allowed params lists'
            gcl.parseClass('''
            class ExplicitSecureBindingController {
                def bindParams() {
                    def target = new Person()
                    secureBindData(target, params, ['name'])
                }

                def bindParamsWithOptions() {
                    def target = new Person()
                    secureBindData([nullMissing: true], target, params, ['name'])
                }

                def bindParamsWithVariableOptions() {
                    def target = new Person()
                    def options = [nullMissing: true]
                    secureBindData(options, target, params, ['name'])
                }

                def bindParamsWithPrefix() {
                    def target = new Person()
                    secureBindData(target, params, ['name'], 'person')
                }

                def bindParamsWithVariableAllowedParams() {
                    def target = new Person()
                    List allowedParams = ['name']
                    secureBindData(target, params, allowedParams)
                }

                def bindParamsWithCollectionAllowedParams() {
                    def target = new Person()
                    Collection allowedParams = ['name']
                    secureBindData(target, params, allowedParams)
                }

                def bindCollection() {
                    def people = []
                    secureBindData(Person, people, request, ['name'])
                }

                def bindCollectionWithVariableTargetType() {
                    def people = []
                    def targetType = Person
                    secureBindData(targetType, people, request, ['name'])
                }

                def bindMapTarget() {
                    secureBindData([:], params, ['name'])
                }

                def bindMapTargetWithNullMissingKey() {
                    secureBindData([nullMissing: true], params, ['name'])
                }
            }

            class Person {
                String name
            }
            ''')

        then:
            noExceptionThrown()
    }

    void 'Test secureBindData options map still requires explicit allowed params'() {
        when: 'A controller calls secureBindData with options but without an allowed params list'
            gcl.parseClass('''
            class OptionsWithoutAllowedParamsController {
                def save() {
                    def target = new Person()
                    secureBindData([nullMissing: true], target, params)
                }
            }

            class Person {
                String name
            }
            ''')

        then:
            MultipleCompilationErrorsException e = thrown()
            e.message.contains 'secureBindData requires an explicit allowedParams list. Use secureBindData(target, params, allowedParams).'
    }

    void 'Test secureBindData rejects non-collection allowed params variable'() {
        when: 'A controller passes a non-collection variable where allowed params are required'
            gcl.parseClass('''
            class NonListAllowedParamsController {
                def save() {
                    def target = new Person()
                    String filter = 'name'
                    secureBindData(target, params, filter)
                }
            }

            class Person {
                String name
            }
            ''')

        then:
            MultipleCompilationErrorsException e = thrown()
            e.message.contains 'secureBindData requires an explicit allowedParams list. Use secureBindData(target, params, allowedParams).'
    }

    void 'GAP: secureBindData does not reject an allowedParams list built from request data'() {
        when: 'A controller builds its allowedParams list from request-controlled data rather than a literal'
            gcl.parseClass('''
            class TaintedAllowedParamsController {
                def save() {
                    def target = new Person()
                    List allowedParams = params.fields?.tokenize(',')
                    secureBindData(target, params, allowedParams)
                }
            }

            class Person {
                String name
            }
            ''')

        then: 'the compiler should reject an allowedParams list that is not a compile-time literal, since callers can smuggle attacker-controlled field names past the framework validation otherwise'
            MultipleCompilationErrorsException e = thrown()
            e.message.contains 'secureBindData requires allowedParams to be a literal list of property names, or a reference to a constant list. A dynamically built list can be attacker-controlled and defeats the purpose of secureBindData.'
    }
}
