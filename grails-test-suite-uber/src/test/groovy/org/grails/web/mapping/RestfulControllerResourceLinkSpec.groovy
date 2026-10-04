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

import grails.artefact.Artefact
import grails.core.DefaultGrailsApplication
import grails.persistence.Entity
import grails.rest.RestfulController
import grails.util.GrailsWebMockUtil
import grails.web.CamelCaseUrlConverter
import grails.web.mapping.UrlCreator
import grails.web.mapping.UrlMappingsHolder
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.grails.datastore.mapping.model.MappingContext
import org.grails.web.util.WebUtils
import org.springframework.web.context.request.RequestContextHolder

import spock.lang.Specification

/**
 * Verifies that resource link resolution works against the real {@link RestfulController} hierarchy,
 * rather than only against a stand-in generic base class.
 */
class RestfulControllerResourceLinkSpec extends Specification {

    static final String BASE_URL = 'https://myserver.com/foo'
    static final String CONTEXT = '/bar'

    DefaultGrailsApplication grailsApplication

    def setup() {
        WebUtils.clearGrailsWebRequest()
        GrailsWebMockUtil.bindMockWebRequest()
        grailsApplication = new DefaultGrailsApplication(
                PeopleRestController,
                LinkArticleController,
                LinkArticleApiController,
                ReviewsController
        ).tap {
            initialise()
        }
    }

    void cleanup() {
        RequestContextHolder.resetRequestAttributes()
        WebUtils.clearGrailsWebRequest()
    }

    void "a controller extending RestfulController is the target of its domain class resource link"() {
        given: 'PeopleRestController is the only controller for LinkPerson'
        def generator = createGenerator()

        expect: 'the real RestfulController generic argument is resolved'
        generator.link(resource: new LinkPerson(), action: 'show') == '/bar/peopleRest/show'
    }

    void "a bounded type parameter is resolved, as used by RestfulServiceController"() {
        given: 'ReviewsController extends a base declaring <T extends GormEntity<T>>, the shape RestfulServiceController uses'
        def generator = createGenerator()

        expect: 'the bound does not prevent the actual type argument being resolved'
        generator.link(resource: new LinkReview(), action: 'show') == '/bar/reviews/show'
    }

    void "a controller named after the domain class still wins"() {
        given: 'LinkArticleController is named for LinkArticle and LinkArticleApiController declares it'
        def generator = createGenerator()

        expect: 'the naming convention is preserved, so existing applications are unaffected'
        generator.link(resource: new LinkArticle(), action: 'show') == '/bar/linkArticle/show'
    }

    private MappingContext createMappingContext() {
        def context = new KeyValueMappingContext('')
        context.addPersistentEntity(LinkPerson)
        context.addPersistentEntity(LinkArticle)
        context.addPersistentEntity(LinkReview)
        context
    }

    private DefaultLinkGenerator createGenerator() {
        def generator = new DefaultLinkGenerator(BASE_URL, CONTEXT)
        generator.grailsUrlConverter = new CamelCaseUrlConverter()
        generator.grailsApplication = grailsApplication
        generator.mappingContext = createMappingContext()
        final callable = { String controller, String action, String namespace, String pluginName, String httpMethod, Map params ->
            [createRelativeURL: { String c, String a, String n, String p, Map parameterValues, String encoding, String fragment ->
                "${namespace ? '/' + namespace : ''}/$controller/$action${parameterValues.id ? '/' + parameterValues.id : ''}".toString()
            }] as UrlCreator
        }
        generator.urlMappingsHolder = [getReverseMapping: callable, getReverseMappingNoDefault: callable] as UrlMappingsHolder
        generator
    }
}

@Entity
class LinkPerson {
    String name
}

@Entity
class LinkArticle {
    String title
}

@Entity
class LinkReview {
    String body
}

/**
 * Mirrors the shape of {@code RestfulServiceController<T extends GormEntity<T>>}, which lives in
 * grails-scaffolding and is therefore not on this module's classpath.
 */
abstract class ServiceBackedControllerBase<T> extends RestfulController<T> {
    ServiceBackedControllerBase(Class<T> resource) {
        super(resource)
    }
}

@Artefact('Controller')
class PeopleRestController extends RestfulController<LinkPerson> {
    PeopleRestController() { super(LinkPerson) }
}

@Artefact('Controller')
class LinkArticleController {
    def index() {}
    def show() {}
}

@Artefact('Controller')
class LinkArticleApiController extends RestfulController<LinkArticle> {
    LinkArticleApiController() { super(LinkArticle) }
}

@Artefact('Controller')
class ReviewsController extends ServiceBackedControllerBase<LinkReview> {
    ReviewsController() { super(LinkReview) }
}
