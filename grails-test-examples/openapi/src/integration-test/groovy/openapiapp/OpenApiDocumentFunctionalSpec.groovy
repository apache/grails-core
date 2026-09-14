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

import spock.lang.Shared
import spock.lang.Specification

import grails.testing.mixin.integration.Integration
import org.apache.grails.testing.http.client.HttpClientSupport

/**
 * Confirms the document a running application serves. The unit tests build the customizer directly;
 * this exercises what an application actually gets - the plugin registering the bean, the bean
 * being wired, and springdoc serving what it contributes.
 */
@Integration
class OpenApiDocumentFunctionalSpec extends Specification implements HttpClientSupport {

    @Shared
    Map document

    def setup() {
        if (document == null) {
            document = http('/v3/api-docs').json()
        }
    }

    void 'the document is served'() {
        expect:
        http('/v3/api-docs').assertStatus(200)
    }

    void 'Swagger UI is served'() {
        expect:
        http('/swagger-ui/index.html').assertStatus(200)
    }

    void 'the resource mapping is described'() {
        expect:
        document.paths.containsKey('/books')
        document.paths.containsKey('/books/{id}')
    }

    void 'the statuses described are the ones the controller answers'() {
        expect: 'created rather than ok, and no content on delete'
        document.paths['/books'].post.responses.containsKey('201')
        document.paths['/books/{id}'].delete.responses.containsKey('204')
        !document.paths['/books/{id}'].delete.responses['204'].containsKey('content')

        and: 'the validation failure a save can answer with'
        document.paths['/books'].post.responses.containsKey('422')

        and: 'and the miss an identifier can produce'
        document.paths['/books/{id}'].get.responses.containsKey('404')
    }

    void 'the listing describes the paging it accepts'() {
        expect:
        document.paths['/books'].get.parameters*.name as Set == ['max', 'offset', 'sort', 'order'] as Set
    }

    void 'the domain class is described from its constraints'() {
        given: 'read with get, since properties resolves to the Map own members otherwise'
        Map book = document.components.schemas.Book
        Map properties = (Map) book.get('properties')

        expect:
        properties.title.maxLength == 120
        properties.genre.enum == ['scifi', 'history']
        book.required == ['title']

        and: 'with the properties the server assigns marked read only'
        properties.id.readOnly
        properties.version.readOnly
    }

    void 'every reference in the document resolves'() {
        given:
        Set defined = (document.components?.schemas ?: [:]).keySet()
        Set referenced = (document.toString() =~ /#\/components\/schemas\/(\w+)/).collect { it[1] } as Set

        expect:
        (referenced - defined).isEmpty()
    }

    void 'no two operations share an identifier'() {
        given:
        List ids = document.paths.values().collectMany { Map methods ->
            methods.values().findResults { it instanceof Map ? it.operationId : null }
        }

        expect:
        ids.size() == ids.toSet().size()
    }

    void 'the described endpoints answer as described'() {
        when: 'a resource is created through the documented operation'
        def created = httpPost('/books', '{"title":"Functional"}', 'application/json')

        then: 'with the status the document claims'
        created.assertStatus(201)

        when: 'and listed through the documented listing'
        def listed = http('/books')

        then:
        listed.assertStatus(200)
        listed.assertContains('Functional')
    }
}
