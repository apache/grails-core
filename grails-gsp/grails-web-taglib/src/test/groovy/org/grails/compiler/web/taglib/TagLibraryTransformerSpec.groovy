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
package org.grails.compiler.web.taglib

import grails.artefact.Artefact
import grails.compiler.ast.ClassInjector
import org.grails.compiler.injection.GrailsAwareClassLoader
import spock.lang.Issue
import spock.lang.Specification

class TagLibraryTransformerSpec extends Specification {

    void 'Test tag methods are created for properties which are tags'() {
        expect:
             /*
              * Tag methods are overloaded with these argument combinations:
              *    tagName()
              *    tagName(Map)
              *    tagName(Closure)
              *    tagName(Map, Closure)
              *    tagName(Map, CharSequence)
              */
           5 == ClosureMethodTestTagLib.methods.findAll { methodName == it.name }.size()

         where:
             methodName << ['closureTagWithNoExplicitArgs', 'closureTagWithOneArg', 'closureTagWithTwoArgs']
    }

    void 'Test tag methods are not created for properties which are not tags'() {
        expect:
           0 == ClosureMethodTestTagLib.methods.findAll { methodName == it.name }.size()

         where:
             methodName << ['closureTagWithThreeArgs', 'closureTagWithFourArgs']
    }
    
    @Issue('GRAILS-11241')
    void 'Test that a tag libary can be marked with @CompileStatic without generating compile errors'() {
        given:
        def gcl = new GrailsAwareClassLoader()
        def transformer = new TagLibraryTransformer() {
            @Override
            boolean shouldInject(URL url) { true }
        }
        gcl.classInjectors = [transformer] as ClassInjector[]

        expect:
        gcl.parseClass('''
        @groovy.transform.CompileStatic
        class StaticallyCompiledTagLib implements grails.artefact.TagLibrary{
            def closureTagWithNoExplicitArgs = { }
            def closureTagWithOneArg = { attrs -> }
            def closureTagWithTwoArgs = { attrs, body -> }
        }
        ''')
    }
}

@Artefact('TagLib')
class ClosureMethodTestTagLib {
    def closureTagWithNoExplicitArgs = { }
    def closureTagWithOneArg = { attrs -> }
    def closureTagWithTwoArgs = { attrs, body -> }
    def closureTagWithThreeArgs = { attrs, body, extra -> }
    def closureTagWithFourArgs = { attrs, body, extra, anotherExtra -> }
}


