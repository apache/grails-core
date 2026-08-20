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
package grails.web.mapping

import grails.config.Settings
import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import grails.util.Holders

import org.grails.config.PropertySourcesConfig
import org.grails.support.MockApplicationContext
import org.grails.web.mapping.DefaultUrlMappingEvaluator
import org.grails.web.mapping.DefaultUrlMappingsHolder
import org.grails.web.util.WebUtils

import org.springframework.http.HttpMethod

import spock.lang.Specification

/**
 * A 'resources' mapping generates PUT and DELETE routes that a browser form cannot reach, because browsers
 * submit only GET and POST. When the hidden HTTP method override is switched off, POST variants of the update
 * and delete routes are generated so those actions stay reachable from a plain form submit.
 */
class PostOverrideVariantResourceMappingSpec extends Specification {

    void setup() {
        WebUtils.clearGrailsWebRequest()
    }

    void cleanup() {
        // setConfig publishes to the static Holders, which would otherwise leak into sibling tests
        Holders.clear()
    }

    void 'no POST variants are generated while the hidden method override is enabled'() {
        given: 'the default configuration'
        def holder = urlMappingsHolder(true) {
            "/books"(resources: 'book')
        }

        expect: 'the mappings are exactly the eight a resources block has always generated'
        holder.urlMappings.size() == 8

        and: 'nothing answers a POST to the member URL'
        holder.matchAll('/books/1', HttpMethod.POST).length == 0
        holder.matchAll('/books/1/delete', HttpMethod.POST).length == 0

        and: 'the real REST routes are untouched'
        holder.matchAll('/books/1', HttpMethod.PUT)[0].actionName == 'update'
        holder.matchAll('/books/1', HttpMethod.DELETE)[0].actionName == 'delete'
    }

    void 'POST variants for update and delete are generated when the override is disabled'() {
        given: 'the override switched off'
        def holder = urlMappingsHolder(false) {
            "/books"(resources: 'book')
        }

        expect: 'two mappings are added'
        holder.urlMappings.size() == 10

        and: 'update reuses the member URL, so a form action does not change'
        holder.matchAll('/books/1', HttpMethod.POST)[0].actionName == 'update'
        holder.matchAll('/books/1', HttpMethod.POST)[0].controllerName == 'book'

        and: 'delete takes a segment of its own, mirroring the edit route'
        holder.matchAll('/books/1/delete', HttpMethod.POST)[0].actionName == 'delete'
        holder.matchAll('/books/1/delete', HttpMethod.POST)[0].controllerName == 'book'

        and: 'the original REST routes still answer'
        holder.matchAll('/books/1', HttpMethod.PUT)[0].actionName == 'update'
        holder.matchAll('/books/1', HttpMethod.DELETE)[0].actionName == 'delete'

        and: 'the collection routes are unaffected'
        holder.matchAll('/books', HttpMethod.POST)[0].actionName == 'save'
        holder.matchAll('/books', HttpMethod.GET)[0].actionName == 'index'
    }

    void 'the id is bound from the POST variant URLs'() {
        given:
        def holder = urlMappingsHolder(false) {
            "/books"(resources: 'book')
        }

        expect:
        holder.matchAll('/books/42', HttpMethod.POST)[0].id == '42'
        holder.matchAll('/books/42/delete', HttpMethod.POST)[0].id == '42'
    }

    void 'no patch variant is generated, because patch and update share a route and an implementation'() {
        given:
        def holder = urlMappingsHolder(false) {
            "/books"(resources: 'book')
        }

        expect: 'the PATCH route remains for real PATCH clients'
        holder.matchAll('/books/1', HttpMethod.PATCH)[0].actionName == 'patch'

        and: 'but a form posting to the member URL reaches update, which is what patch delegates to'
        holder.matchAll('/books/1/patch', HttpMethod.POST).length == 0
    }

    void 'excludes suppresses the POST variant along with the route it shadows'() {
        given:
        def holder = urlMappingsHolder(false) {
            "/books"(resources: 'book', excludes: ['delete'])
        }

        expect: 'neither the DELETE route nor its POST variant exists'
        holder.matchAll('/books/1', HttpMethod.DELETE).length == 0
        holder.matchAll('/books/1/delete', HttpMethod.POST).length == 0

        and: 'update is untouched'
        holder.matchAll('/books/1', HttpMethod.POST)[0].actionName == 'update'
    }

    void 'includes limits the POST variants to the actions listed'() {
        given:
        def holder = urlMappingsHolder(false) {
            "/books"(resources: 'book', includes: ['index', 'show'])
        }

        expect:
        holder.matchAll('/books/1', HttpMethod.POST).length == 0
        holder.matchAll('/books/1/delete', HttpMethod.POST).length == 0
    }

    void 'a single resource gives update its own segment, because POST is already the save route'() {
        given: 'a singular resource, which has no id segment'
        def holder = urlMappingsHolder(false) {
            "/book"(resource: 'book')
        }

        expect: 'save keeps the bare POST route'
        holder.matchAll('/book', HttpMethod.POST)[0].actionName == 'save'

        and: 'update and delete each take a segment'
        holder.matchAll('/book/update', HttpMethod.POST)[0].actionName == 'update'
        holder.matchAll('/book/delete', HttpMethod.POST)[0].actionName == 'delete'

        and: 'the REST routes are unchanged'
        holder.matchAll('/book', HttpMethod.PUT)[0].actionName == 'update'
        holder.matchAll('/book', HttpMethod.DELETE)[0].actionName == 'delete'
    }

    void 'nested resources generate variants at every level'() {
        given:
        def holder = urlMappingsHolder(false) {
            "/authors"(resources: 'author') {
                "/books"(resources: 'book')
            }
        }

        expect: 'the nested member URL answers a POST for update'
        holder.matchAll('/authors/1/books/2', HttpMethod.POST)[0].actionName == 'update'
        holder.matchAll('/authors/1/books/2', HttpMethod.POST)[0].controllerName == 'book'

        and: 'and the nested delete segment resolves'
        holder.matchAll('/authors/1/books/2/delete', HttpMethod.POST)[0].actionName == 'delete'

        and: 'the parent resource gets its own variants'
        holder.matchAll('/authors/1', HttpMethod.POST)[0].actionName == 'update'
        holder.matchAll('/authors/1/delete', HttpMethod.POST)[0].actionName == 'delete'
    }

    private UrlMappingsHolder urlMappingsHolder(boolean overrideEnabled, Closure mappings) {
        def config = new PropertySourcesConfig()
        config.merge([(Settings.WEB_HIDDEN_METHOD_FILTER_ENABLED): overrideEnabled])

        def application = new DefaultGrailsApplication()
        application.config = config

        def ctx = new MockApplicationContext()
        ctx.registerMockBean(GrailsApplication.APPLICATION_ID, application)

        def evaluator = new DefaultUrlMappingEvaluator(ctx)
        new DefaultUrlMappingsHolder(evaluator.evaluateMappings(mappings))
    }
}
