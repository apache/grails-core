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
package grails.test.web

import grails.artefact.Artefact
import grails.persistence.Entity
import grails.testing.gorm.DomainUnitTest
import grails.testing.web.controllers.ControllerUnitTest
import spock.lang.Specification

/**
 * @author Graeme Rocher
 */
class RedirectToDomainSpec extends Specification implements ControllerUnitTest<BookController>, DomainUnitTest<Book> {

    void "Test redirect to domain"() {
        given:"A domain instance"
            def b = new Book().save(flush:true)

        when:"A redirect is issued"
            controller.index()

        then:"The correct link is produced"
            response.redirectUrl == '/book/show/1'
    }
}

@Artefact("Controller")
class BookController {
    def index() {
        def b = Book.get(1)
        redirect b
    }
}
@Entity
class Book {

}
