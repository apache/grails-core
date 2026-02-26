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
package org.grails.web.taglib

import grails.artefact.Artefact
import grails.compiler.GrailsCompileStatic
import grails.testing.web.taglib.TagLibUnitTest
import spock.lang.Specification

class MethodDefinedTagLibSpec extends Specification implements TagLibUnitTest<MethodTagLib> {

    void setupSpec() {
        mockTagLibs(MethodTagLib, SharedNsMethodTagLib, SharedNsClosureTagLib, StaticMethodTagLib)
    }

    void "method tag can use implicit attrs"() {
        expect:
        applyTemplate('<g:methodTag blah="duh" />') == 'duh - is this'
    }

    void "method tag can bind named attribute to typed argument"() {
        expect:
        applyTemplate('<g:typedTag blah="duh" />') == 'duh - typed'
    }
    void "method tag can bind multiple named attributes to multiple typed arguments"() {
        expect:
        applyTemplate('<g:multiTypedTag first="hello" second="world" />') == 'hello-world'
    }

    void "method tag can bind map-valued attribute to map-typed argument by parameter name"() {
        expect:
        applyTemplate('<g:mapValueTag config="${[k:\'v\']}" />') == 'v'
    }
    void "method tag still supports reserved attrs map parameter"() {
        expect:
        applyTemplate('<g:attrsMapTag blah="duh" />') == 'duh'
    }

    void "method tag can use implicit body closure"() {
        expect:
        applyTemplate('<g:bodyTag>abc</g:bodyTag>') == 'before-abc-after'
    }

    void "closure tag remains supported"() {
        expect:
        applyTemplate('<g:legacyTag blah="duh" />') == 'legacy-duh'
    }

    void "multiple taglibs sharing the same namespace resolve independently"() {
        expect:
        applyTemplate('<shared:fromMethod one="1" /> <shared:fromClosure two="2" />') == 'method-1 closure-2'
    }

    void "statically compiled method tag can use implicit attrs and typed args"() {
        expect:
        applyTemplate('<g:staticImplicitTag blah="duh" /> <g:staticTypedTag blah="duh2" />') == 'duh - static implicit duh2 - static typed'
    }

    void "statically compiled method tag can render body"() {
        expect:
        applyTemplate('<g:staticBodyTag>abc</g:staticBodyTag>') == 'before-abc-after'
    }
}

@GrailsCompileStatic
@Artefact('TagLib')
class StaticMethodTagLib {
    def staticImplicitTag() {
        Map tagAttrs = (Map) propertyMissing('attrs')
        out << "${tagAttrs.blah} - static implicit"
    }

    def staticTypedTag(String blah) {
        out << "${blah} - static typed"
    }

    def staticBodyTag() {
        Closure tagBody = (Closure) propertyMissing('body')
        out << "before-${tagBody?.call()}-after"
    }
}

@Artefact('TagLib')
class MethodTagLib {
    def methodTag() {
        out << "${attrs.blah} - is this"
    }

    def typedTag(String blah) {
        out << "${blah} - typed"
    }
    def multiTypedTag(String first, String second) {
        out << "${first}-${second}"
    }

    def mapValueTag(Map config) {
        out << "${config.k}"
    }

    def attrsMapTag(Map attrs) {
        out << "${attrs.blah}"
    }

    def bodyTag() {
        out << "before-${body()}-after"
    }

    Closure legacyTag = { attrs, body ->
        out << "legacy-${attrs.blah}"
    }
}

@Artefact('TagLib')
class SharedNsMethodTagLib {
    static namespace = 'shared'

    def fromMethod(String one) {
        out << "method-${one}"
    }
}

@Artefact('TagLib')
class SharedNsClosureTagLib {
    static namespace = 'shared'

    Closure fromClosure = { attrs ->
        out << "closure-${attrs.two}"
    }
}
