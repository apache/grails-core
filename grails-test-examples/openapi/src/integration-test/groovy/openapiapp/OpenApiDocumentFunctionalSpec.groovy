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

    void 'the springdoc paths are served past a catch-all mapping without the application excluding them'() {
        expect: 'the catch-all answers any other path'
        http('/anything/else').assertEquals(200, 'fallback')

        and: 'springdoc answers its own'
        http('/v3/api-docs').json().openapi == '3.1.0'
        http('/v3/api-docs/catalog').json().openapi == '3.1.0'
        http('/v3/api-docs.yaml').assertContains(200, 'openapi: 3.1.0')
        http('/swagger-ui.html').assertContains(200, 'swagger-ui')
        http('/swagger-ui/index.html').assertContains(200, 'swagger-ui')
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

    void 'the listing describes the paging it accepts, and the parameter the action declares'() {
        expect:
        document.paths['/books'].get.parameters*.name as Set == ['max', 'offset', 'sort', 'order', 'genre'] as Set
    }

    void 'the identifier is described as the type the domain class declares'() {
        given:
        Map<String, Map> operations = document.paths['/books/{id}']

        expect: 'on every operation, including one whose annotation describes it'
        operations.values().every { Map operation ->
            operation.parameters.find { it.name == 'id' }.schema == [type: 'integer', format: 'int64']
        }
        operations.get.parameters.find { it.name == 'id' }.description == 'The identifier of the book'
    }

    void 'the document starts from the configured base document'() {
        expect:
        document.info.title == 'Catalog API'
        document.info.version == '2.0.0'
        document.security == [[Bearer: []]]
        document.components.securitySchemes.Bearer.scheme == 'bearer'
    }

    void 'a response the controller declares applies to each of its actions'() {
        expect:
        document.paths['/books'].get.responses['401'].description == 'Not signed in'
        document.paths['/books/{id}'].delete.responses['401']

        and: 'but not to another controller'
        !document.paths['/authors'].get.responses.containsKey('401')
    }

    void 'the form actions an HTML client uses are not described'() {
        expect:
        !document.paths.containsKey('/books/create')
        !document.paths.containsKey('/books/{id}/edit')
        !document.paths.containsKey('/book/create')
    }

    void 'a read-only controller is described with only what it serves'() {
        expect: 'the reads, and none of the writes'
        document.paths['/publishers'].keySet() == ['get'] as Set
        document.paths['/publishers/{id}'].keySet() == ['get'] as Set
        document.paths.keySet().findAll { String path -> path.startsWith('/publisher/') } ==
                ['/publisher/index', '/publisher/show/{id}'] as Set

        and: 'the default mapping answers the path of the controller alone with its index'
        document.paths['/publisher'].keySet() == ['get'] as Set
        http('/publisher').assertStatus(200)

        and: 'a write the document leaves out is one the controller refuses'
        httpPostJson('/publishers', '{"name":"Ace"}').assertStatus(405)
    }

    void 'the routes of the default mapping are described'() {
        expect:
        document.paths['/book/show/{id}'].get
        document.paths['/book/save'].post
    }

    void 'a group describes only what it selects'() {
        when:
        Map group = http('/v3/api-docs/catalog').json()

        then:
        group.paths.keySet() == ['/books', '/books/{id}'] as Set
    }

    void 'a group springdoc is configured with describes only what it selects'() {
        when:
        Map group = http('/v3/api-docs/authors').json()

        then:
        group.paths.keySet() == ['/authors', '/authors/{id}'] as Set
    }

    void 'a springdoc method filter leaves an action out of the default document'() {
        expect: 'the action is served, but not described'
        http('/notes/audit').assertStatus(204)
        !document.paths.containsKey('/notes/audit')
    }

    void 'a group applies its method filters to the Grails actions'() {
        when:
        Map group = http('/v3/api-docs/book-reads').json()

        then:
        group.paths.collectMany { String path, Map item -> item.keySet().collect { "${it} ${path}".toString() } } as Set ==
                ['get /books', 'get /books/{id}'] as Set
    }

    void 'a springdoc operation customizer is given the handler method of each action'() {
        expect: 'the controller, and the method the action is declared as'
        document.paths['/books'].get['x-action'] == 'BookController.index'
        document.paths['/books/{id}'].get['x-action'] == 'BookController.show'
        document.paths['/notes/review'].post['x-action'] == 'NoteController.review'
    }

    void 'a customizer the application declares sees the Grails operations'() {
        given:
        int described = document.paths.values().sum { Map item -> item.size() } as int
        Map group = http('/v3/api-docs/book-reads').json()

        expect: 'in the default document, and in a group, which springdoc gives the global customizers first'
        document['x-operation-count'] == described
        group['x-operation-count'] == 2
    }

    void 'a group applies its operation customizers, and the global ones, to the Grails operations'() {
        when:
        Map group = http('/v3/api-docs/book-reads').json()

        then:
        group.paths['/books'].get['x-group'] == 'book-reads'
        group.paths['/books'].get['x-action'] == 'BookController.index'

        and: 'and its own document customizers'
        group['x-audience'] == 'readers'
    }

    void 'a group selects the operations that consume the media types it asks for'() {
        when:
        Map group = http('/v3/api-docs/author-changes').json()

        then: 'only the operations that bind a JSON body'
        group.paths.collectMany { String path, Map item -> item.keySet().collect { "${it} ${path}".toString() } } as Set ==
                ['post /authors', 'put /authors/{id}', 'post /authors/{id}', 'patch /authors/{id}'] as Set
    }

    void 'an association is described the way Grails renders and binds it'() {
        given:
        Map reference = (Map) ((Map) document.components.schemas.Book.get('properties')).author

        when: 'an author is created, and a book bound to it by the described reference'
        Map author = httpPostJson('/authors', '{"name":"Le Guin"}').assertStatus(201).json()
        Map book = httpPostJson('/books', "{\"title\":\"The Dispossessed\",\"author\":{\"id\":${author.id}}}")
                .assertStatus(201).json()

        then: 'the book renders the association as the described reference'
        book.author == [id: author.id]
        ((Map) reference.get('properties')).keySet() == ['id'] as Set
        reference.required == ['id']
    }

    void 'a failed validation answers with the described errors'() {
        given:
        Map described = (Map) document.components.schemas.ValidationErrors
        Map item = (Map) ((Map) ((Map) described.get('properties')).errors).items

        when:
        Map errors = httpPostJson('/books', '{"genre":"poetry"}').assertStatus(422).json()

        then:
        errors.keySet() == ((Map) described.get('properties')).keySet()
        errors.errors
        errors.errors.every { Map error ->
            ((Map) item.get('properties')).keySet().containsAll(error.keySet()) && error.keySet().containsAll(item.required)
        }
    }

    void 'the domain class is described from its constraints'() {
        given: 'read with get, since properties resolves to the Map own members otherwise'
        Map book = document.components.schemas.Book
        Map properties = (Map) book.get('properties')

        expect:
        properties.title.maxLength == 120
        properties.genre.enum == ['scifi', 'history', null]
        properties.title.minLength == 1
        book.required as Set == ['title', 'dateCreated', 'lastUpdated'] as Set

        and: 'with the properties the server assigns marked read only'
        properties.id.readOnly
        properties.dateCreated.readOnly
        properties.lastUpdated.readOnly

        and: 'the version, which Grails does not render, left out'
        !properties.containsKey('version')
    }

    void 'what data binding does not bind is described as read only, and is not bound'() {
        given:
        Map properties = (Map) document.components.schemas.Book.get('properties')

        when:
        Map book = httpPostJson('/books', '{"title":"Kindred","shelfCode":"A1","dateCreated":"2000-01-01T00:00:00Z"}')
                .assertStatus(201).json()

        then:
        properties.shelfCode.readOnly
        !properties.title.readOnly
        book.shelfCode == null
        !book.dateCreated.startsWith('2000')
    }

    void 'classes sharing a simple name are each described under their package'() {
        given:
        Map schemas = document.components.schemas

        expect: 'neither takes the name they share'
        !schemas.containsKey('Note')

        and: 'each action binds the one it declares'
        document.paths['/notes/review'].post.requestBody.content['application/json'].schema.$ref ==
                '#/components/schemas/openapiapp.reviews.Note'
        document.paths['/notes/draft'].post.requestBody.content['application/json'].schema.$ref ==
                '#/components/schemas/openapiapp.drafts.Note'
        ((Map) schemas['openapiapp.reviews.Note'].get('properties')).keySet() == ['text'] as Set
        ((Map) schemas['openapiapp.drafts.Note'].get('properties')).keySet() == ['body', 'revision'] as Set
    }

    void 'every reference in the document resolves'() {
        given:
        Set defined = (document.components?.schemas ?: [:]).keySet()
        Set referenced = (document.toString() =~ /#\/components\/schemas\/([\w.\-]+)/).collect { it[1] } as Set

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
