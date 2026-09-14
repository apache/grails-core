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
package org.grails.web.mapping

import grails.core.DefaultGrailsApplication
import grails.util.GrailsWebMockUtil
import grails.web.CamelCaseUrlConverter
import grails.web.mapping.UrlCreator
import grails.web.mapping.UrlMappingsHolder
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.grails.datastore.mapping.model.MappingContext
import org.grails.web.mapping.domainlink.AdminGadgetsController
import org.grails.web.mapping.domainlink.Chapter
import org.grails.web.mapping.domainlink.ChapterApiController
import org.grails.web.mapping.domainlink.ChapterController
import org.grails.web.mapping.domainlink.Chronicle
import org.grails.web.mapping.domainlink.ChroniclesController
import org.grails.web.mapping.domainlink.Gadget
import org.grails.web.mapping.domainlink.GadgetsController
import org.grails.web.mapping.domainlink.Note
import org.grails.web.mapping.domainlink.NoteController
import org.grails.web.mapping.domainlink.PeopleController
import org.grails.web.mapping.domainlink.Person
import org.grails.web.mapping.domainlink.Tag
import org.grails.web.mapping.domainlink.TagsController
import org.grails.web.mapping.domainlink.Widget
import org.grails.web.mapping.domainlink.WidgetsController
import org.grails.web.util.WebUtils
import org.springframework.web.context.request.RequestContextHolder

import spock.lang.Specification

/**
 * Tests that a {@code resource} link targets the controller that actually exposes the domain class,
 * rather than assuming the controller is named after the domain class.
 */
class LinkGeneratorResourceControllerSpec extends Specification {

    static final String BASE_URL = 'https://myserver.com/foo'
    static final String CONTEXT = '/bar'

    DefaultGrailsApplication grailsApplication

    def setup() {
        WebUtils.clearGrailsWebRequest()
        GrailsWebMockUtil.bindMockWebRequest()
        grailsApplication = new DefaultGrailsApplication(
                PeopleController,
                WidgetsController,
                GadgetsController,
                AdminGadgetsController,
                NoteController,
                ChapterController,
                ChapterApiController,
                TagsController,
                ChroniclesController
        ).tap {
            initialise()
        }
    }

    void cleanup() {
        RequestContextHolder.resetRequestAttributes()
        WebUtils.clearGrailsWebRequest()
    }

    def "a resource link targets the controller declaring the domain class, not the domain class name"() {
        given: 'PeopleController is the only controller declaring Person'
        def generator = createGenerator()

        expect: 'the link targets people rather than person'
        generator.link(resource: new Person(id: 1), action: 'show') == '/bar/people/show/1'
    }

    def "the domain class is resolved through an intermediate base class"() {
        given: 'WidgetsController reaches Widget through WidgetControllerBase'
        def generator = createGenerator()

        expect: 'the intermediate class does not hide the domain class'
        generator.link(resource: new Widget(id: 2), action: 'show') == '/bar/widgets/show/2'
    }

    def "an ambiguous domain class falls back to the domain class name"() {
        given: 'both GadgetsController and AdminGadgetsController declare Gadget'
        def generator = createGenerator()

        expect: 'no controller is inferred, preserving the existing behaviour'
        generator.link(resource: new Gadget(id: 3), action: 'show') == '/bar/gadget/show/3'
    }

    def "a domain class no controller declares falls back to the domain class name"() {
        given: 'NoteController declares no domain class'
        def generator = createGenerator()

        expect: 'the domain class name is used, as before'
        generator.link(resource: new Note(id: 4), action: 'show') == '/bar/note/show/4'
    }

    def "an explicit controller attribute overrides the resolved controller"() {
        given: 'Person would otherwise resolve to people'
        def generator = createGenerator()

        expect: 'the explicit controller wins'
        generator.link(resource: new Person(id: 5), controller: 'note', action: 'show') == '/bar/note/show/5'
    }

    def "a controller named after the domain class wins over one that declares it"() {
        given: 'ChapterController is named for Chapter, and ChapterApiController declares it'
        def generator = createGenerator()

        expect: 'the naming convention wins, so an application relying on it is unaffected'
        generator.link(resource: new Chapter(id: 6), action: 'show') == '/bar/chapter/show/6'
    }

    def "the domain class is resolved through a generic interface"() {
        given: 'TagsController declares Tag through an interface rather than a superclass'
        def generator = createGenerator()

        expect: 'interfaces are walked too, as Groovy traits compile to interfaces'
        generator.link(resource: new Tag(id: 7), action: 'show') == '/bar/tags/show/7'
    }

    def "a base class declaring two domain classes does not claim the wrong one"() {
        given: 'ChroniclesController extends a base parameterised on both Person and Chronicle'
        def generator = createGenerator()

        expect: 'the ambiguous level is skipped and the resource comes from further up the hierarchy'
        generator.link(resource: new Chronicle(id: 8), action: 'show') == '/bar/chronicles/show/8'

        and: 'the other type argument is not claimed by that controller'
        generator.link(resource: new Person(id: 9), action: 'show') == '/bar/people/show/9'
    }

    def "a domain class passed instead of an instance resolves the same controller"() {
        given: 'the domain class rather than an instance, as used for an uninitialised association'
        def generator = createGenerator()

        expect: 'the same controller is resolved, so a lazy proxy and a loaded instance agree'
        generator.link(resource: Person, id: 1, action: 'show') == '/bar/people/show/1'
        generator.link(resource: new Person(id: 1), action: 'show') == '/bar/people/show/1'

        and: 'the naming convention still wins for a domain class'
        generator.link(resource: Chapter, id: 2, action: 'show') == '/bar/chapter/show/2'
    }

    def "resolution requires a mapping context"() {
        given: 'the same link generated with and without a mapping context'
        def person = new Person(id: 10)

        expect: 'the mapping context is what enables resolution'
        createGenerator(true).link(resource: person, action: 'show') == '/bar/people/show/10'
        createGenerator(false).link(resource: person, action: 'show') != '/bar/people/show/10'
    }

    def "re-registering controllers rebuilds the index"() {
        given: 'an index built while PeopleController is registered'
        def generator = createGenerator()

        expect: 'it resolves'
        generator.link(resource: new Person(id: 11), action: 'show') == '/bar/people/show/11'

        when: 'PeopleController is no longer registered'
        generator.grailsApplication = new DefaultGrailsApplication(NoteController).tap { initialise() }

        then: 'the stale mapping is not reused'
        generator.link(resource: new Person(id: 11), action: 'show') != '/bar/people/show/11'
    }

    private MappingContext createMappingContext() {
        def context = new KeyValueMappingContext('')
        context.addPersistentEntity(Person)
        context.addPersistentEntity(Widget)
        context.addPersistentEntity(Gadget)
        context.addPersistentEntity(Note)
        context.addPersistentEntity(Chapter)
        context.addPersistentEntity(Tag)
        context.addPersistentEntity(Chronicle)
        context
    }

    private DefaultLinkGenerator createGenerator(boolean withMappingContext = true) {
        def generator = new DefaultLinkGenerator(BASE_URL, CONTEXT)
        generator.grailsUrlConverter = new CamelCaseUrlConverter()
        generator.grailsApplication = grailsApplication
        if (withMappingContext) {
            generator.mappingContext = createMappingContext()
        }
        final callable = { String controller, String action, String namespace, String pluginName, String httpMethod, Map params ->
            [createRelativeURL: { String c, String a, String n, String p, Map parameterValues, String encoding, String fragment ->
                "${namespace ? '/' + namespace : ''}/$controller/$action${parameterValues.id ? '/' + parameterValues.id : ''}".toString()
            }] as UrlCreator
        }
        generator.urlMappingsHolder = [getReverseMapping: callable, getReverseMappingNoDefault: callable] as UrlMappingsHolder
        generator
    }
}
