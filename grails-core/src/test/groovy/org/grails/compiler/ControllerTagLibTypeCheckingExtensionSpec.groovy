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

class ControllerTagLibTypeCheckingExtensionSpec extends Specification {

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
}
