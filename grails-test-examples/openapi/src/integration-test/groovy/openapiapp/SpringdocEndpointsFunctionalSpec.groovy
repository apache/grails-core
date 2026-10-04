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
package openapiapp

import spock.lang.Specification

import grails.testing.mixin.integration.Integration
import org.apache.grails.testing.http.client.HttpClientSupport

/**
 * Confirms how the documents springdoc serves describe its own Spring MVC endpoints, which Jackson
 * renders, beside the Grails endpoints, which Grails renders.
 */
@Integration
class SpringdocEndpointsFunctionalSpec extends Specification implements HttpClientSupport {

    void 'a domain class a Spring MVC endpoint and a Grails endpoint both use is described once, as Grails renders it'() {
        when:
        Map shelf = http('/v3/api-docs/shelf').json()

        then: 'both refer to one schema'
        response(shelf, '/spring/books/{id}') == '#/components/schemas/Book'
        response(shelf, '/books/{id}') == '#/components/schemas/Book'
        !shelf.components.schemas.keySet().any { String name -> name.endsWith('.Book') }

        and: 'as Grails renders the class'
        Map book = shelf.components.schemas.Book as Map
        book.get('properties').id.readOnly
        book.get('properties').author.get('properties').keySet() == ['id'] as Set
        book.required as Set == ['title', 'dateCreated', 'lastUpdated'] as Set
    }

    void 'a domain class only a Spring MVC endpoint returns is described as Jackson renders it'() {
        when:
        Map shelf = http('/v3/api-docs/shelf').json()
        Map magazine = shelf.components.schemas.Magazine as Map

        then: 'with what it has that is not persisted, and the whole of what it is associated with'
        magazine.get('properties').headline
        magazine.get('properties').publisher.'$ref' == '#/components/schemas/Publisher'
    }

    void 'the validation errors are described apart from a class of that name a Spring MVC endpoint returns'() {
        when:
        Map shelf = http('/v3/api-docs/shelf').json()

        then: 'the class keeps its name, as the Spring MVC endpoint describes it'
        shelf.paths['/spring/checks'].post.responses['200'].content['application/json'].schema.'$ref' ==
                '#/components/schemas/ValidationErrors'
        shelf.components.schemas.ValidationErrors.get('properties').keySet() == ['count', 'problems'] as Set

        and: 'the Grails endpoints answer a failed validation with the errors, under the name of their class'
        shelf.paths['/books'].post.responses['422'].content['application/json'].schema.'$ref' ==
                '#/components/schemas/grails.validation.ValidationErrors'
        shelf.components.schemas['grails.validation.ValidationErrors'].get('properties').errors
    }

    void 'a class springdoc described for one document is not taken for another class of its name in the next'() {
        given: 'the shelf group, whose Spring MVC endpoint returns the domain class Book, described first'
        http('/v3/api-docs/shelf').json()

        when: 'a group whose Spring MVC endpoint returns another class named Book'
        Map legacy = http('/v3/api-docs/legacy').json()

        then: 'the Spring MVC endpoint refers to the class it returns'
        response(legacy, '/legacy/books/{isbn}') == '#/components/schemas/Book'
        legacy.components.schemas.Book.get('properties').keySet() == ['isbn', 'name'] as Set

        and: 'the Grails endpoints to the domain class, named apart from it'
        response(legacy, '/books/{id}') == '#/components/schemas/openapiapp.Book'
        legacy.components.schemas['openapiapp.Book'].get('properties').title
    }

    void 'a converter of the application sees the schema the Grails converter described'() {
        when:
        Map document = http('/v3/api-docs').json()

        then:
        document.components.schemas.Book.'x-required-seen' == document.components.schemas.Book.required
    }

    private static String response(Map document, String path) {
        document.paths[path].get.responses['200'].content['application/json'].schema.'$ref'
    }
}
