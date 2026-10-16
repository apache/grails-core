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
package org.grails.testing.runtime.support

import org.springframework.context.support.GenericApplicationContext
import spock.lang.Specification

import grails.core.DefaultGrailsApplication
import org.grails.plugins.web.GroovyPagesGrailsPlugin
import org.grails.plugins.web.taglib.ApplicationTagLib

class LazyTagLibraryLookupSpec extends Specification {

    GenericApplicationContext applicationContext = new GenericApplicationContext()
    LazyTagLibraryLookup lookup = new LazyTagLibraryLookup()

    void setup() {
        DefaultGrailsApplication application = new DefaultGrailsApplication()
        application.initialise()
        applicationContext.beanFactory.registerSingleton('grailsApplication', application)
        applicationContext.refresh()
        lookup.grailsApplication = application
        lookup.applicationContext = applicationContext
        lookup.afterPropertiesSet()
    }

    void cleanup() {
        lookup.cleanTagLibsMetaClass()
        applicationContext.close()
    }

    void 'the provided gsp tag libraries are registered lazily'() {
        expect:
        lookup.tagLibClasses.containsAll(new GroovyPagesGrailsPlugin().providedArtefacts as List)
        lookup.hasNamespace('g')
        lookup.getAvailableTags('g').empty
        !applicationContext.containsBean(ApplicationTagLib.name)
        lookup.lookupTagLibrary('g', 'noSuchTag') == null
        lookup.lookupTagLibrary('nope', 'link') == null

        when:
        Object tagLib = lookup.lookupTagLibrary('g', 'link')

        then:
        tagLib instanceof ApplicationTagLib
        applicationContext.containsBean(ApplicationTagLib.name)
        applicationContext.getBean(ApplicationTagLib.name).is(tagLib)
        lookup.lookupTagLibrary('g', 'link').is(tagLib)
        lookup.getAvailableTags('g').contains('link')
    }

    void 'clearing the lookup keeps the lazy registrations'() {
        given:
        lookup.lookupTagLibrary('g', 'link')

        when:
        lookup.clear()

        then:
        lookup.getAvailableTags('g').empty
        lookup.hasNamespace('g')
        lookup.lookupNamespaceDispatcher('tmpl') != null
        lookup.lookupTagLibrary('g', 'link') instanceof ApplicationTagLib
    }

}
