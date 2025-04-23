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

import grails.gorm.transactions.Rollback
import org.grails.datastore.mapping.mongo.MongoDatastore
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import static org.springframework.http.MediaType.APPLICATION_JSON
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * This test relies on a local instance of MongoDB running
 */
class BookControllerSpec extends Specification {

    @Shared MongoDatastore datastore

    BookController bookController = new BookController(bookService: datastore.getService(BookService))

    @Rollback
    void "test find by title"() {
        given:
        def mockMvc = MockMvcBuilders.standaloneSetup(bookController).build()
        Book.DB.drop()
        Book.saveAll(new Book(title: "The Stand"), new Book(title: "It"))
        datastore.currentSession.flush()

        when:
        def response = mockMvc.perform(get("/books/It"))

        then:
        response
            .andExpect(status().isOk())
            .andExpect(content().contentType(APPLICATION_JSON))
            .andExpect(content().json('{"title":"It","id":2}'))

    }

}
