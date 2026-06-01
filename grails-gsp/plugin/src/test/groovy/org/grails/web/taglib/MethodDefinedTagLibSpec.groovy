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
import grails.gsp.Tag
import grails.testing.web.taglib.TagLibUnitTest
import org.grails.taglib.GrailsTagException
import spock.lang.Specification

class MethodDefinedTagLibSpec extends Specification implements TagLibUnitTest<MethodTagLib> {

    void setupSpec() {
        mockTagLibs(MethodTagLib, SharedNsMethodTagLib, SharedNsClosureTagLib, StaticMethodTagLib)
    }

    void "method tag can use implicit attrs"() {
        expect:
        applyTemplate('<g:methodTag blah="duh" />') == 'duh - is this'
    }

    void "zero-arg method tags require explicit annotation"() {
        when:
        applyTemplate('<g:unannotatedZeroArg/>')

        then:
        thrown(GrailsTagException)
    }

    void "method tag can bind named attribute to typed argument"() {
        expect:
        applyTemplate('<g:typedTag blah="duh" />') == 'duh - typed'
    }

    void "method tag can bind multiple named attributes to multiple typed arguments"() {
        expect:
        applyTemplate('<g:multiTypedTag first="hello" second="world" />') == 'hello-world'
    }

    void "method tag does not bind a single mismatched attribute by position"() {
        when:
        applyTemplate('<g:mismatchedArgTag foo="bar" />')

        then:
        thrown(GrailsTagException)
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

    void "private and protected methods are not exposed as tags"() {
        when:
        applyTemplate('<g:privateOnlyTag/>')
        then:
        thrown(GrailsTagException)

        when:
        applyTemplate('<g:protectedOnlyTag/>')
        then:
        thrown(GrailsTagException)
    }

    void "object methods are not exposed as method tags"() {
        when:
        applyTemplate('<g:toString/>')

        then:
        thrown(GrailsTagException)
    }

    void "namespace metaproperty does not shadow a real getter"() {
        expect:
        applyTemplate('<g:readSharedProperty/>') == 'real-shared'
    }
}

@GrailsCompileStatic
@Artefact('TagLib')
class StaticMethodTagLib {
    @Tag
    def staticImplicitTag() {
        Map tagAttrs = (Map) propertyMissing('attrs')
        out << "${tagAttrs.blah} - static implicit"
    }

    @Tag
    def staticTypedTag(String blah) {
        out << "${blah} - static typed"
    }

    @Tag
    def staticBodyTag() {
        Closure tagBody = (Closure) propertyMissing('body')
        out << "before-${tagBody?.call()}-after"
    }
}

@Artefact('TagLib')
class MethodTagLib {
    String getShared() {
        'real-shared'
    }

    @Tag
    def methodTag() {
        out << "${attrs.blah} - is this"
    }

    def unannotatedZeroArg() {
        out << 'not a tag'
    }

    @Tag
    def typedTag(String blah) {
        out << "${blah} - typed"
    }

    @Tag
    def multiTypedTag(String first, String second) {
        out << "${first}-${second}"
    }

    @Tag
    def mismatchedArgTag(String name) {
        out << name
    }

    @Tag
    def mapValueTag(Map config) {
        out << "${config.k}"
    }

    def attrsMapTag(Map attrs) {
        out << "${attrs.blah}"
    }

    private def privateOnlyTag() {
        out << 'private'
    }

    protected def protectedOnlyTag() {
        out << 'protected'
    }

    @Tag
    def bodyTag() {
        out << "before-${body()}-after"
    }

    @Tag
    def readSharedProperty() {
        out << shared
    }

    String toString() {
        'object-method'
    }

    Closure legacyTag = { attrs, body ->
        out << "legacy-${attrs.blah}"
    }
}

@Artefact('TagLib')
class SharedNsMethodTagLib {
    static namespace = 'shared'

    @Tag
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
