/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.grails.data.testing.tck.tests

import org.apache.grails.data.testing.tck.domains.Book
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec

/**
 * @author graemerocher
 */
class NegationSpec extends GrailsDataTckSpec {

    void "Test negation in dynamic finder"() {
        given:
        new Book(title: "The Stand", author: "Stephen King").save()
        new Book(title: "The Shining", author: "Stephen King").save()
        new Book(title: "Along Came a Spider", author: "James Patterson").save()

        when:
        def results = Book.findAllByAuthorNotEqual("James Patterson")
        def author = Book.findByAuthorNotEqual("Stephen King")

        then:
        results.size() == 2
        results[0].author == "Stephen King"
        results[1].author == "Stephen King"

        author != null
        author.author == "James Patterson"
    }

    void "Test simple negation in criteria"() {
        given:
        new Book(title: "The Stand", author: "Stephen King").save()
        new Book(title: "The Shining", author: "Stephen King").save()
        new Book(title: "Along Came a Spider", author: "James Patterson").save()

        when:
        def results = Book.withCriteria { ne("author", "James Patterson") }
        def author = Book.createCriteria().get { ne("author", "Stephen King") }

        then:
        results.size() == 2
        results[0].author == "Stephen King"
        results[1].author == "Stephen King"

        author != null
        author.author == "James Patterson"
    }

    void "Test complex negation in criteria"() {
        given:
        new Book(title: "The Stand", author: "Stephen King").save()
        new Book(title: "The Shining", author: "Stephen King").save()
        new Book(title: "Along Came a Spider", author: "James Patterson").save()
        new Book(title: "The Girl with the Dragon Tattoo", author: "Stieg Larsson").save()

        when:
        def results = Book.withCriteria {
            not {
                eq 'title', 'The Stand'
                eq 'author', 'James Patterson'
            }
        }

        then:
        results.size() == 2
        results.find { it.author == "Stieg Larsson" } != null
        results.find { it.author == "Stephen King" && it.title == "The Shining" } != null
    }
}
