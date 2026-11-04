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

import grails.core.DefaultGrailsApplication
import grails.core.gsp.GrailsTagLibClass
import grails.gsp.TagLib
import org.grails.core.gsp.DefaultGrailsTagLibClass
import org.grails.taglib.TagLibraryLookup
import spock.lang.Specification

/**
 * Registering a tag library uses the tags recorded when it was compiled, and falls back to
 * discovering them from the class when there is no such record.
 *
 * <p>The fallback is what keeps a plugin built before the index existed, and a tag library registered
 * while an application is being developed, working unchanged.
 */
class TagLibraryLookupIndexSpec extends Specification {

    void 'a tag library with no descriptor is still registered from the class'() {
        given: 'a tag library compiled in this test source set, which carries no descriptor'
        TagLibraryLookup lookup = newLookup(FallbackTagLib)

        when:
        lookup.registerTagLib(new DefaultGrailsTagLibClass(FallbackTagLib))

        then: 'its tags are discovered the previous way, so nothing regresses without an index'
        lookup.lookupTagLibrary('fallback', 'discovered') != null
    }

    void 'registration reports the same tags whichever route was taken'() {
        given:
        TagLibraryLookup lookup = newLookup(FallbackTagLib)
        lookup.registerTagLib(new DefaultGrailsTagLibClass(FallbackTagLib))

        expect: 'the set matches what the class itself declares'
        lookup.getAvailableTags('fallback') ==
                new DefaultGrailsTagLibClass(FallbackTagLib).tagNames

        and: 'a method that is not a tag is absent either way'
        !('helper' in lookup.getAvailableTags('fallback'))
    }

    void 'a tag library registered after startup is picked up'() {
        given: 'a lookup that has already registered one tag library'
        TagLibraryLookup lookup = newLookup(FallbackTagLib)
        lookup.registerTagLib(new DefaultGrailsTagLibClass(FallbackTagLib))

        when: 'another is registered later, as reloading during development does'
        lookup.registerTagLib(new DefaultGrailsTagLibClass(LateTagLib))

        then:
        lookup.lookupTagLibrary('late', 'arrived') != null
    }

    void 'a string body is accepted by the namespaced dispatcher'() {
        given: 'a dispatcher for a namespace, as a statically compiled page uses'
        TagLibraryLookup lookup = newLookup(BodyTagLib)
        lookup.registerTagLib(new DefaultGrailsTagLibClass(BodyTagLib))
        def dispatcher = new org.grails.taglib.TagLibNamespaceMethodDispatcher(
                'body', lookup, org.grails.taglib.encoder.OutputContextLookupHelper.lookupOutputContext())

        when: 'the tag is called with a string body rather than a closure'
        dispatcher.invokeMethod('wrap', [[:], 'text body'] as Object[])

        then: 'it is adapted rather than failing to cast'
        noExceptionThrown()
    }

    private static TagLibraryLookup newLookup(Class<?>... tagLibClasses) {
        def lookup = new TagLibraryLookup() {
            @Override
            protected void putTagLib(Map<String, Object> tags, String name, GrailsTagLibClass taglib) {
                tags.put(name, taglib.newInstance())
            }
        }
        def application = new DefaultGrailsApplication(tagLibClasses, TagLibraryLookup.classLoader)
        application.initialise()
        lookup.grailsApplication = application
        lookup
    }
}

@TagLib
class FallbackTagLib {
    static namespace = 'fallback'
    def discovered(Map attrs) { 'discovered' }
    String helper(String a, int b) { a }
}

@TagLib
class BodyTagLib {
    static namespace = 'body'
    def wrap(Map attrs, Closure body) { body() }
}

@TagLib
class LateTagLib {
    static namespace = 'late'
    def arrived(Map attrs) { 'arrived' }
}
