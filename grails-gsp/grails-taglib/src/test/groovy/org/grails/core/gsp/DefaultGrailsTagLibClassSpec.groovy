/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  'License'); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.core.gsp

import groovy.transform.CompileStatic

import spock.lang.Issue
import spock.lang.Specification

class DefaultGrailsTagLibClassSpec extends Specification {

    def 'tag discovery finds Closure properties in a dynamic TagLib'() {
        when:
        def tagLibClass = new DefaultGrailsTagLibClass(DynamicSampleTagLib)

        then:
        tagLibClass.tagNames.contains('myTag')
        tagLibClass.tagNames.contains('anotherTag')
        !tagLibClass.tagNames.contains('nonClosureProperty')
        !tagLibClass.tagNames.contains('someStaticClosure')
    }

    @Issue('https://github.com/apache/grails-core/issues/15506')
    def 'tag discovery finds Closure properties in a @CompileStatic TagLib'() {
        when:
        def tagLibClass = new DefaultGrailsTagLibClass(CompileStaticSampleTagLib)

        then:
        tagLibClass.tagNames.contains('staticTag')
        tagLibClass.tagNames.contains('anotherStaticTag')
        !tagLibClass.tagNames.contains('nonClosureField')
        !tagLibClass.tagNames.contains('someStaticClosure')
    }

    @Issue('https://github.com/apache/grails-core/issues/15506')
    def 'tag discovery finds Closure properties from both parent and child classes with @CompileStatic'() {
        when:
        def tagLibClass = new DefaultGrailsTagLibClass(ChildCompileStaticTagLib)

        then:
        tagLibClass.tagNames.contains('parentTag')
        tagLibClass.tagNames.contains('childTag')
    }

    def 'namespace is correctly read from TagLib'() {
        when:
        def tagLibClass = new DefaultGrailsTagLibClass(CustomNamespaceTagLib)

        then:
        tagLibClass.namespace == 'custom'
    }

    def 'returnObjectForTags is correctly read from TagLib'() {
        when:
        def tagLibClass = new DefaultGrailsTagLibClass(DynamicSampleTagLib)

        then:
        tagLibClass.tagNamesThatReturnObject.contains('myTag')
    }
}

class DynamicSampleTagLib {

    static returnObjectForTags = ['myTag']

    Closure myTag = { attrs -> 'hello' }
    Closure anotherTag = { attrs, body -> }
    String nonClosureProperty = 'not a tag'
    static Closure someStaticClosure = { -> }
}

@CompileStatic
class CompileStaticSampleTagLib {

    Closure staticTag = { Map attrs -> 'compiled' }
    Closure anotherStaticTag = { Map attrs, body -> }
    String nonClosureField = 'not a tag'
    static Closure someStaticClosure = { -> }
}

@CompileStatic
class ParentCompileStaticTagLib {

    Closure parentTag = { Map attrs -> 'parent' }
}

@CompileStatic
class ChildCompileStaticTagLib extends ParentCompileStaticTagLib {

    Closure childTag = { Map attrs -> 'child' }
}

class CustomNamespaceTagLib {

    static String namespace = 'custom'
    Closure myTag = { attrs -> }
}
