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

import grails.artefact.gsp.TagLibraryInvoker
import grails.core.DefaultGrailsApplication
import grails.core.gsp.GrailsTagLibClass
import grails.gsp.TagLib
import grails.util.GrailsWebMockUtil
import org.grails.core.gsp.DefaultGrailsTagLibClass
import org.grails.taglib.TagLibraryLookup
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.springframework.web.context.request.RequestContextHolder
import spock.lang.Specification

/**
 * What a class calling a tag through {@code methodMissing} gets back.
 *
 * <p>Resolving the tag used to end in {@code tagLibrary.invokeMethod(name, args)} - a direct call on
 * the tag library bean. For a tag declared as a closure that reached the generated wrapper and so
 * captured output; for one declared as a method there is no wrapper, so it called the method and
 * returned whatever the method itself returned, with nothing captured. Dispatch now goes through the
 * same capture for both, so a method-declared tag returns what it wrote rather than its return value.
 *
 * <p>That is the intended behaviour - the two forms of declaring a tag should not answer differently -
 * but it is a change to a public trait, so it is pinned here.
 */
class TagLibraryInvokerDispatchSpec extends Specification {

    GrailsWebRequest webRequest

    def setup() {
        // out resolves through the current request, so a tag that writes needs one bound.
        webRequest = GrailsWebMockUtil.bindMockWebRequest()
    }

    def cleanup() {
        RequestContextHolder.resetRequestAttributes()
    }

    void 'a method declared tag called unqualified returns what it wrote'() {
        given: 'a class that can call tags but is not itself a tag library, as a controller is'
        Caller caller = new Caller(tagLibraryLookup: newLookup())

        when: 'the tag writes to out and returns something else entirely'
        Object result = caller.callWriting()

        then: 'the captured output is the answer, not the return value of the method'
        result.toString() == 'written'
    }

    void 'a method declared tag receives the attributes it was called with'() {
        given:
        Caller caller = new Caller(tagLibraryLookup: newLookup())

        when:
        Object result = caller.callWithAttributes()

        then:
        result.toString() == 'hello world'
    }

    void 'a name that is both a tag and an overload reaches the overload'() {
        given: 'format is declared as a tag and as an ordinary two argument method'
        Caller caller = new Caller(tagLibraryLookup: newLookup())

        when: 'called with the arguments only the overload can take'
        Object result = caller.callOverload()

        then: 'the tag shape does not match, so the real method runs rather than the tag with nothing'
        result == '2026-08-19/yyyy'
    }

    void 'a name whose shapes overlap resolves to the tag rather than the overload'() {
        given: 'format is declared as a tag and as a one argument method, and a lone CharSequence is a body'
        Caller caller = new Caller(tagLibraryLookup: newLookup())

        when:
        Object result = caller.callOverlappingOverload()

        then: 'the tag runs and its output is captured, which is how a page has always dispatched this'
        result.toString() == 'as a tag'
    }

    void 'a name no tag library declares is still a missing method'() {
        given:
        Caller caller = new Caller(tagLibraryLookup: newLookup())

        when:
        caller.callUnknown()

        then:
        thrown(MissingMethodException)
    }

    private static TagLibraryLookup newLookup() {
        TagLibraryLookup lookup = new TagLibraryLookup() {
            @Override
            protected void putTagLib(Map<String, Object> tags, String name, GrailsTagLibClass taglib) {
                tags.put(name, taglib.newInstance())
            }
        }
        DefaultGrailsApplication application =
                new DefaultGrailsApplication([DispatchTagLib] as Class[], TagLibraryLookup.classLoader)
        application.initialise()
        lookup.grailsApplication = application
        lookup.registerTagLib(new DefaultGrailsTagLibClass(DispatchTagLib))
        lookup
    }
}

class Caller implements TagLibraryInvoker {

    Object callWriting() {
        writes()
    }

    Object callWithAttributes() {
        greet(name: 'world')
    }

    Object callUnknown() {
        noSuchTagAnywhere()
    }

    Object callOverload() {
        format('2026-08-19', 'yyyy')
    }

    Object callOverlappingOverload() {
        format('2026-08-19')
    }
}

@TagLib
class DispatchTagLib {

    static namespace = 'g'

    def writes(Map attrs) {
        out << 'written'
        'a return value that is not the output'
    }

    def greet(Map attrs) {
        out << "hello ${attrs.name}"
    }

    def format(Map attrs) {
        out << 'as a tag'
    }

    def format(String value, String pattern) {
        "${value}/${pattern}"
    }

    def format(String value) {
        "only the overload"
    }
}
