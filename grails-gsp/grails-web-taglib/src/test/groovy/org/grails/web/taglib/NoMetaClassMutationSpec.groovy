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

import groovy.lang.ExpandoMetaClass
import grails.core.DefaultGrailsApplication
import grails.core.gsp.GrailsTagLibClass
import grails.gsp.TagLib
import org.grails.core.gsp.DefaultGrailsTagLibClass
import org.grails.taglib.NamespacedTagDispatcher
import org.grails.taglib.TagLibraryLookup
import spock.lang.Specification

/**
 * Resolving a tag must not write to a metaclass.
 *
 * <p>Each dispatcher used to be built with its own ExpandoMetaClass carrying a method per tag, and
 * every caller had the tags it used installed onto its own metaclass on first use. That made tag
 * dispatch a read of an initialised ExpandoMetaClass, which is guarded by a read-write lock and was
 * the largest single contended cost in profiles of concurrent rendering.
 */
class NoMetaClassMutationSpec extends Specification {

    void 'creating a namespace dispatcher does not build a metaclass for it'() {
        given:
        TagLibraryLookup lookup = newLookup()

        when:
        NamespacedTagDispatcher dispatcher =
                new NamespacedTagDispatcher('quiet', null, lookup.grailsApplication, lookup)

        then: 'no per instance ExpandoMetaClass is created and populated'
        !(dispatcher.metaClass instanceof ExpandoMetaClass) ||
                !dispatcher.metaClass.hasMetaMethod('ping', [Map] as Class[])
    }

    void 'the dispatchers a lookup creates carry no tag methods'() {
        given:
        TagLibraryLookup lookup = newLookup()
        lookup.registerTagLib(new DefaultGrailsTagLibClass(QuietTagLib))

        when:
        NamespacedTagDispatcher dispatcher = lookup.lookupNamespaceDispatcher('quiet')

        then: 'the tag is reachable'
        dispatcher != null
        lookup.lookupTagLibrary('quiet', 'ping') != null

        and: 'without a method having been installed for it'
        !(dispatcher.metaClass instanceof ExpandoMetaClass) ||
                !dispatcher.metaClass.hasMetaMethod('ping', [Map] as Class[])
    }

    void 'the template namespace resolves without installing the template name'() {
        given:
        TagLibraryLookup lookup = newLookup()
        def dispatcher = new org.grails.taglib.TemplateNamespacedTagDispatcher(
                QuietTagLib, lookup.grailsApplication, lookup)

        expect: 'using a template name does not add a method for it'
        !(dispatcher.metaClass instanceof ExpandoMetaClass) ||
                !dispatcher.metaClass.hasMetaMethod('someTemplate', [Map] as Class[])
    }

    void 'a page resolves an unqualified tag through a declared method'() {
        expect: 'declared rather than installed, so compiling a page writes to no metaclass'
        org.grails.gsp.GroovyPage.getDeclaredMethod('methodMissing', String, Object) != null
    }

    private static TagLibraryLookup newLookup() {
        def lookup = new TagLibraryLookup() {
            @Override
            protected void putTagLib(Map<String, Object> tags, String name, GrailsTagLibClass taglib) {
                tags.put(name, taglib.newInstance())
            }
        }
        def application = new DefaultGrailsApplication([QuietTagLib] as Class[], TagLibraryLookup.classLoader)
        application.initialise()
        lookup.grailsApplication = application
        lookup
    }
}

@TagLib
class QuietTagLib {
    static namespace = 'quiet'
    def ping(Map attrs) { 'pong' }
}
