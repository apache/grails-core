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
package example

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

import spock.lang.Shared
import spock.lang.Specification
import tools.jackson.databind.json.JsonMapper

import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext

import org.grails.datastore.mapping.mongo.MongoDatastore

/**
 * Starts the application as {@code main} does, against the MongoDB the tests share, so that Spring Boot rather than
 * the test wires GORM: the datastore, the {@code BookService} GORM implements, and the controller it is injected into.
 */
class ApplicationSpec extends Specification {

    /**
     * Its own database, apart from the one {@link BookControllerSpec} drops and fills.
     */
    static final String DATABASE = 'springbootApplication'

    @Shared
    ConfigurableApplicationContext context

    @Shared
    HttpClient http = HttpClient.newHttpClient()

    @Shared
    JsonMapper json = JsonMapper.builder().build()

    void setupSpec() {
        context = SpringApplication.run(Application,
                '--server.port=0',
                "--spring.mongodb.uri=${SpringBootStartMongoExtension.dbContainer.connectionString}",
                "--grails.mongodb.databaseName=${DATABASE}")
    }

    void cleanupSpec() {
        context?.getBean(MongoDatastore)?.mongoClient?.getDatabase(DATABASE)?.drop()
        context?.close()
        http?.close()
    }

    void 'Spring injects the BookService GORM implements into the controller'() {
        expect:
        context.getBean(MongoDatastore)
        context.getBean(BookController).bookService.is(context.getBean(BookService))
    }

    void 'the books the application saves as it starts are listed'() {
        when:
        HttpResponse<String> response = get('/books')

        then:
        response.statusCode() == 200
        json.readValue(response.body(), List)*.title.sort() == ['It', 'The Shining', 'The Stand']
    }

    void 'a book is found by its title through the BookService'() {
        when:
        HttpResponse<String> response = get('/books/It')

        then:
        response.statusCode() == 200
        json.readValue(response.body(), Map).title == 'It'
    }

    private HttpResponse<String> get(String path) {
        int port = context.environment.getProperty('local.server.port', Integer)
        http.send(HttpRequest.newBuilder(URI.create("http://localhost:${port}${path}")).GET().build(),
                HttpResponse.BodyHandlers.ofString())
    }
}
