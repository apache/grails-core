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
package org.grails.compiler

import org.codehaus.groovy.control.MultipleCompilationErrorsException
import spock.lang.Specification

class TagLibraryInvokerTypeCheckingExtensionSpec extends Specification {

    void 'undefined method on a declared service field still fails compilation'() {
        given:
        def gcl = new GroovyClassLoader(getClass().classLoader)

        when: 'compiling a @GrailsCompileStatic controller that calls a non-existent method on a declared service'
        gcl.parseClass('''
            import grails.compiler.GrailsCompileStatic

            class BookService {
                List list() { [] }
            }

            @GrailsCompileStatic
            class BookController {
                BookService bookService

                def action() {
                    bookService.nonExistentMethod()
                }
            }
        ''')

        then: 'compilation fails — the extension does not silence errors on resolved types'
        thrown(MultipleCompilationErrorsException)
    }

    void 'type mismatch still fails compilation in a @GrailsCompileStatic controller'() {
        given:
        def gcl = new GroovyClassLoader(getClass().classLoader)

        when:
        gcl.parseClass('''
            import grails.compiler.GrailsCompileStatic

            @GrailsCompileStatic
            class SomeController {
                def action() {
                    int x = "not a number"
                }
            }
        ''')

        then:
        thrown(MultipleCompilationErrorsException)
    }

    void 'undefined method on a declared local variable still fails compilation'() {
        given:
        def gcl = new GroovyClassLoader(getClass().classLoader)

        when: 'compiling a @GrailsCompileStatic controller with a wrong method on a resolved String variable'
        gcl.parseClass('''
            import grails.compiler.GrailsCompileStatic

            @GrailsCompileStatic
            class AnotherController {
                def action() {
                    String title = "Grails in Action"
                    title.nonExistentMethod()
                }
            }
        ''')

        then: 'compilation fails — static type checking is preserved for declared locals'
        thrown(MultipleCompilationErrorsException)
    }

    void 'tag extension defers to another extension that resolves the same unrecognised call'() {
        given:
        def gcl = new GroovyClassLoader(getClass().classLoader)

        when: 'a controller is compiled with both the catch-all tag extension and another extension that resolves unrecognised calls'
        def c = gcl.parseClass('''
            import groovy.transform.CompileStatic

            @CompileStatic(extensions = [
                'org.grails.compiler.StubDslTypeCheckingExtension',
                'org.grails.compiler.TagLibraryInvokerTypeCheckingExtension'
            ])
            class JobController {
                def index() {
                    someDslMethod 'scope', 'asc'
                }
            }
        ''')

        then: 'no "Reference to method is ambiguous" error — the catch-all defers instead of producing a second candidate node'
        c
    }

    void 'tag extension still resolves tag calls when no other extension claims them'() {
        given:
        def gcl = new GroovyClassLoader(getClass().classLoader)

        when: 'a controller invokes an unresolved call with only the tag extension active'
        def c = gcl.parseClass('''
            import groovy.transform.CompileStatic

            @CompileStatic(extensions = ['org.grails.compiler.TagLibraryInvokerTypeCheckingExtension'])
            class BookController {
                def index() {
                    link(controller: 'home')
                }
            }
        ''')

        then: 'compilation succeeds — the tag call is made dynamic'
        c
    }

    void 'a @GrailsCompileStatic tag library can invoke a tag on this'() {
        given:
        def gcl = new GroovyClassLoader(getClass().classLoader)

        when: 'a @GrailsCompileStatic taglib with a method-based tag invokes a tag on this'
        def c = gcl.parseClass('''
            import grails.compiler.GrailsCompileStatic

            @GrailsCompileStatic
            class GreetingTagLib {
                static namespace = "greet"

                def hello(Map attrs) {
                    render(template: '/shared/header')
                }
            }
        ''')

        then: 'compilation succeeds — tag dispatch in the taglib is made dynamic'
        c
    }

    void 'a @GrailsCompileStatic tag library can invoke a namespaced tag'() {
        given:
        def gcl = new GroovyClassLoader(getClass().classLoader)

        when: 'a @GrailsCompileStatic taglib with a method-based tag invokes a tag via a namespace dispatcher'
        def c = gcl.parseClass('''
            import grails.compiler.GrailsCompileStatic

            @GrailsCompileStatic
            class MessageTagLib {

                def shout(Map attrs) {
                    g.message(code: 'greeting')
                }
            }
        ''')

        then: 'compilation succeeds — the namespaced tag call is made dynamic'
        c
    }

    void 'type errors are still caught in a @GrailsCompileStatic tag library'() {
        given:
        def gcl = new GroovyClassLoader(getClass().classLoader)

        when: 'a @GrailsCompileStatic taglib contains a static type error'
        gcl.parseClass('''
            import grails.compiler.GrailsCompileStatic

            @GrailsCompileStatic
            class BrokenTagLib {

                def render(Map attrs) {
                    int count = "not a number"
                }
            }
        ''')

        then: 'compilation fails — static type checking is preserved in tag libraries'
        thrown(MultipleCompilationErrorsException)
    }

    void 'a deprecated closure-field tag that dispatches tags fails cleanly rather than crashing the compiler'() {
        given:
        def gcl = new GroovyClassLoader(getClass().classLoader)

        when: 'a @GrailsCompileStatic taglib defines a tag the deprecated way - as a closure field - that invokes a tag'
        gcl.parseClass('''
            import grails.compiler.GrailsCompileStatic

            @GrailsCompileStatic
            class LegacyTagLib {

                Closure hello = { attrs ->
                    render(template: '/shared/header')
                }
            }
        ''')

        then: 'a normal compilation error is reported (the extension defers because there is no enclosing method), not a compiler NPE'
        MultipleCompilationErrorsException e = thrown()
        !e.message.contains('NullPointerException')
    }
}
