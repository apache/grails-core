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
package grails.plugin.json.view.api

import grails.persistence.Entity
import grails.plugin.json.view.test.JsonRenderResult
import grails.plugin.json.view.test.JsonViewTest
import groovy.json.JsonException
import org.grails.testing.GrailsUnitTest
import spock.lang.Specification

class JsonApiHandleAssociationsSpec extends Specification implements JsonViewTest, GrailsUnitTest {
    void setup() {
        mappingContext.addPersistentEntities(Author, PublishedBook, Publisher)
    }

    void 'more than one associated objects should produce valid JSON'() {
        given:
            PublishedBook returnOfTheKing = new PublishedBook(
                    title: 'The Return of the King',
                    author: new Author(name: "J.R.R. Tolkien"),
                    publisher: new Publisher(name: 'George Allen & Unwin')
            )
            returnOfTheKing.id = 3
            returnOfTheKing.author.id = 9
            returnOfTheKing.publisher.id = 81


        when:
            JsonRenderResult result = render('''
import grails.plugin.json.view.api.PublishedBook
model {
    PublishedBook book
}

json jsonapi.render(book)
''', [book: returnOfTheKing])

        then: 'should not throw exception'
            notThrown(JsonException)
    }

}


@Entity
class PublishedBook {
    String title
    Author author
    Publisher publisher
}


@Entity
class Publisher {
    String name
}

