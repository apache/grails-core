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
import grails.gsp.TagLib
import org.grails.core.gsp.DefaultGrailsTagLibClass
import org.grails.taglib.TagLibraryLookup
import spock.lang.Specification

/**
 * Characterises what happens when more than one tag library declares the same namespace and tag,
 * as two plugins and an application overriding a plugin's tag both do.
 *
 * <p>This behaviour is the constraint any compile-time tag index has to respect. If a compiler
 * resolved a duplicated tag to one tag library while the runtime dispatched to another, code would
 * compile against one implementation and run against a different one. Nothing may change here
 * without a deliberate decision, so it is pinned down before anything depends on it.
 */
class TagPrecedenceSpec extends Specification {

    void 'the tag library registered last wins a duplicated namespace and tag'() {
        given:
        TagLibraryLookup lookup = newLookup()

        when: 'two tag libraries declaring the same namespace and tag are registered in order'
        lookup.registerTagLib(new DefaultGrailsTagLibClass(FirstDuplicateTagLib))
        lookup.registerTagLib(new DefaultGrailsTagLibClass(SecondDuplicateTagLib))

        then: 'the later registration provides the tag'
        lookup.lookupTagLibrary('dup', 'shared').getClass() == SecondDuplicateTagLib

        and: 'a tag only the earlier one declares is still reachable'
        lookup.lookupTagLibrary('dup', 'onlyFirst').getClass() == FirstDuplicateTagLib
    }

    void 'registration order alone decides the winner, not declaration order within a namespace'() {
        given:
        TagLibraryLookup lookup = newLookup()

        when: 'the same two tag libraries are registered the other way round'
        lookup.registerTagLib(new DefaultGrailsTagLibClass(SecondDuplicateTagLib))
        lookup.registerTagLib(new DefaultGrailsTagLibClass(FirstDuplicateTagLib))

        then: 'the winner flips, so precedence is positional and carries no inherent ranking'
        lookup.lookupTagLibrary('dup', 'shared').getClass() == FirstDuplicateTagLib
    }

    void 'returnObjectForTags follows the winning tag library rather than accumulating'() {
        given:
        TagLibraryLookup lookup = newLookup()

        when: 'the first declares the shared tag as returning an object and the second does not'
        lookup.registerTagLib(new DefaultGrailsTagLibClass(FirstDuplicateTagLib))
        lookup.registerTagLib(new DefaultGrailsTagLibClass(SecondDuplicateTagLib))

        then: 'the later registration resets it, so the two settings do not merge'
        !lookup.doesTagReturnObject('dup', 'shared')

        when: 'registered the other way round'
        TagLibraryLookup reversed = newLookup()
        reversed.registerTagLib(new DefaultGrailsTagLibClass(SecondDuplicateTagLib))
        reversed.registerTagLib(new DefaultGrailsTagLibClass(FirstDuplicateTagLib))

        then:
        reversed.doesTagReturnObject('dup', 'shared')
    }

    private static TagLibraryLookup newLookup() {
        def lookup = new TagLibraryLookup() {
            @Override
            protected void putTagLib(Map<String, Object> tags, String name, grails.core.gsp.GrailsTagLibClass taglib) {
                tags.put(name, taglib.newInstance())
            }
        }
        def application = new DefaultGrailsApplication(
                [FirstDuplicateTagLib, SecondDuplicateTagLib] as Class[], TagLibraryLookup.classLoader)
        application.initialise()
        lookup.grailsApplication = application
        lookup
    }
}

@TagLib
class FirstDuplicateTagLib {
    static namespace = 'dup'
    static returnObjectForTags = ['shared']
    def shared(Map attrs) { 'first' }
    def onlyFirst(Map attrs) { 'onlyFirst' }
}

@TagLib
class SecondDuplicateTagLib {
    static namespace = 'dup'
    def shared(Map attrs) { 'second' }
}
