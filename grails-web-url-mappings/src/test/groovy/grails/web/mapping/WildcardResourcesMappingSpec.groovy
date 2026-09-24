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

import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import grails.web.mapping.exceptions.UrlMappingException
import org.grails.support.MockApplicationContext
import org.grails.web.mapping.DefaultUrlMappingEvaluator
import org.grails.web.mapping.DefaultUrlMappingsHolder
import org.grails.web.util.WebUtils

import spock.lang.Specification

/**
 * Tests the wildcard form of the {@code resources} mapping, which applies the RESTful resource
 * conventions to every controller rather than to one named controller.
 */
class WildcardResourcesMappingSpec extends Specification {

    def setup() {
        WebUtils.clearGrailsWebRequest()
    }

    void "a wildcard resources mapping generates the resource mappings once"() {
        given: 'a wildcard resources mapping'
        def holder = getUrlMappingsHolder {
            "/$controller"(resources: '*')
        }

        when: 'the generated mappings are inspected'
        def mappings = holder.urlMappings

        then: 'the same set whatever controllers exist, plus the POST member route the disabled filter adds'
        mappings.size() == 9

        and: 'they carry the resource conventions'
        mappings.collect { [it.httpMethod, it.actionName, it.urlData.urlPattern] }.toSet() == [
                ['GET', 'index', '/(*)(.(*))?'],
                ['POST', 'save', '/(*)(.(*))?'],
                ['GET', 'create', '/(*)/create'],
                ['GET', 'edit', '/(*)/(*)/edit'],
                ['GET', 'show', '/(*)/(*)(.(*))?'],
                ['PUT', 'update', '/(*)/(*)(.(*))?'],
                ['POST', 'update', '/(*)/(*)(.(*))?'],
                ['PATCH', 'patch', '/(*)/(*)(.(*))?'],
                ['DELETE', 'delete', '/(*)/(*)(.(*))?']
        ].toSet()
    }

    void "a wildcard resources mapping resolves the controller from the request URL"() {
        given: 'a wildcard resources mapping'
        def holder = getUrlMappingsHolder {
            "/$controller"(resources: '*')
        }

        expect: 'each verb resolves to its conventional action for any controller'
        holder.matchAll('/books', 'GET')[0].actionName == 'index'
        holder.matchAll('/books', 'GET')[0].parameters.controller == 'books'
        holder.matchAll('/books', 'POST')[0].actionName == 'save'
        holder.matchAll('/books/create', 'GET')[0].actionName == 'create'
        holder.matchAll('/books/1', 'GET')[0].actionName == 'show'
        holder.matchAll('/books/1', 'GET')[0].parameters.id == '1'
        holder.matchAll('/books/1/edit', 'GET')[0].actionName == 'edit'
        holder.matchAll('/books/1', 'PUT')[0].actionName == 'update'
        holder.matchAll('/books/1', 'PATCH')[0].actionName == 'patch'
        holder.matchAll('/books/1', 'DELETE')[0].actionName == 'delete'

        and: 'a different controller uses the same mappings'
        holder.matchAll('/authors/2', 'GET')[0].parameters.controller == 'authors'
        holder.matchAll('/authors/2', 'GET')[0].actionName == 'show'
    }

    void "a wildcard resources mapping captures the format"() {
        given: 'a wildcard resources mapping'
        def holder = getUrlMappingsHolder {
            "/$controller"(resources: '*')
        }

        expect: 'the format is bound rather than swallowed by another parameter'
        holder.matchAll('/books.json', 'GET')[0].parameters.format == 'json'
        holder.matchAll('/books.json', 'GET')[0].parameters.controller == 'books'
        holder.matchAll('/books/1.xml', 'GET')[0].parameters.format == 'xml'
        holder.matchAll('/books/1.xml', 'GET')[0].parameters.id == '1'
    }

    void "a wildcard resources mapping honours excludes"() {
        given: 'the form routes are excluded'
        def holder = getUrlMappingsHolder {
            "/$controller"(resources: '*', excludes: ['create', 'edit'])
        }

        expect: 'only the API mappings are generated'
        holder.urlMappings.size() == 7
        holder.urlMappings.every { it.actionName != 'create' && it.actionName != 'edit' }
    }

    void "a wildcard resources mapping is rejected when the URL does not capture the controller"() {
        when: 'the URL names a fixed path instead of capturing the controller'
        getUrlMappingsHolder {
            "/books"(resources: '*')
        }

        then: 'the mapping is rejected rather than silently producing mappings with no controller'
        def e = thrown(UrlMappingException)
        e.message.contains('requires the URL to capture the controller')
    }

    void "nesting within a wildcard resources mapping is rejected"() {
        when: 'a child resource is nested within the wildcard'
        getUrlMappingsHolder {
            "/$controller"(resources: '*') {
                "/authors"(resources: 'author')
            }
        }

        then: 'the mapping is rejected rather than generating a nullId constraint'
        def e = thrown(UrlMappingException)
        e.message.contains('Cannot nest mappings within the wildcard resources mapping')
    }

    void "a wildcard resources mapping composes with a group prefix"() {
        given: 'the wildcard is nested inside a versioned group'
        def holder = getUrlMappingsHolder {
            group "/api/v1", {
                "/$controller"(resources: '*')
            }
        }

        expect: 'the mappings are generated below the group prefix'
        holder.urlMappings.size() == 9
        holder.matchAll('/api/v1/books', 'GET')[0].actionName == 'index'
        holder.matchAll('/api/v1/books', 'GET')[0].parameters.controller == 'books'
        holder.matchAll('/api/v1/books/1', 'DELETE')[0].actionName == 'delete'

        and: 'the ungrouped URL does not match'
        !holder.matchAll('/books', 'GET')
    }

    void "a wildcard resources mapping captures a namespace alongside the controller"() {
        given: 'the URL captures the namespace as well as the controller'
        def holder = getUrlMappingsHolder {
            "/$namespace/$controller"(resources: '*')
        }

        expect: 'they are still generated once'
        holder.urlMappings.size() == 9

        and: 'the namespace and controller are both bound from the URL'
        holder.matchAll('/v1/books', 'GET')[0].actionName == 'index'
        holder.matchAll('/v1/books', 'GET')[0].parameters.namespace == 'v1'
        holder.matchAll('/v1/books', 'GET')[0].parameters.controller == 'books'

        and: 'the id and format still bind in the right positions'
        holder.matchAll('/v1/books/1', 'GET')[0].actionName == 'show'
        holder.matchAll('/v1/books/1', 'GET')[0].parameters.id == '1'
        holder.matchAll('/v1/books/1.json', 'PUT')[0].actionName == 'update'
        holder.matchAll('/v1/books/1.json', 'PUT')[0].parameters.format == 'json'
        holder.matchAll('/v1/books/1.json', 'PUT')[0].parameters.namespace == 'v1'

        and: 'the literal segments still take precedence over the id'
        holder.matchAll('/v1/books/create', 'GET')[0].actionName == 'create'
        holder.matchAll('/v1/books/1/edit', 'GET')[0].actionName == 'edit'

        and: 'a different namespace uses the same mappings'
        holder.matchAll('/v2/authors/2', 'DELETE')[0].actionName == 'delete'
        holder.matchAll('/v2/authors/2', 'DELETE')[0].parameters.namespace == 'v2'
    }

    void "a named resources mapping is unaffected"() {
        given: 'an ordinary named resources mapping'
        def holder = getUrlMappingsHolder {
            "/books"(resources: 'book')
        }

        expect: 'it still binds the controller by name'
        holder.urlMappings.size() == 9
        holder.matchAll('/books/1', 'GET')[0].controllerName == 'book'
    }

    private UrlMappingsHolder getUrlMappingsHolder(Closure mappings) {
        def ctx = new MockApplicationContext()
        ctx.registerMockBean(GrailsApplication.APPLICATION_ID, new DefaultGrailsApplication())
        def evaluator = new DefaultUrlMappingEvaluator(ctx)
        new DefaultUrlMappingsHolder(evaluator.evaluateMappings(mappings))
    }
}
