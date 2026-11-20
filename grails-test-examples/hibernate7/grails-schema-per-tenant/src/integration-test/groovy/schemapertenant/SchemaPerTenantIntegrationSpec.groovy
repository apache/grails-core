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

package schemapertenant

import grails.core.GrailsApplication
import grails.gorm.transactions.Rollback
import grails.testing.mixin.integration.Integration
import grails.util.GrailsWebMockUtil
import groovy.util.logging.Slf4j
import org.grails.datastore.mapping.multitenancy.exceptions.TenantNotFoundException
import org.grails.datastore.mapping.multitenancy.web.SessionTenantResolver
import org.grails.orm.hibernate.HibernateDatastore
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.springframework.web.context.request.RequestContextHolder
import spock.lang.Specification

@Integration(applicationClass = Application)
@Slf4j
@Rollback
class SchemaPerTenantIntegrationSpec extends Specification {
    BookService bookService
    AnotherBookService anotherBookService
    GrailsWebRequest webRequest
    HibernateDatastore hibernateDatastore
    GrailsApplication grailsApplication

    def setup() {
        //To register MimeTypes
        if (grailsApplication.mainContext.parent) {
            grailsApplication.mainContext.getBean("mimeTypesHolder")
        }
        hibernateDatastore.addTenantForSchema("moreBooks")
        hibernateDatastore.addTenantForSchema("evenMoreBooks")
        webRequest = GrailsWebMockUtil.bindMockWebRequest()
    }

    def cleanup() {
        RequestContextHolder.setRequestAttributes(null)
    }

    @Rollback("moreBooks")
    void "test saveBook with data service"() {
        given:
        webRequest.session.setAttribute(SessionTenantResolver.ATTRIBUTE, "moreBooks")

        when:
        Book book = bookService.saveBook("Book-Test-${System.currentTimeMillis()}")
        println book
        log.info("${book}")

        then:
        bookService.countBooks() == 1
        book?.id
    }

    @Rollback("moreBooks")
    void "test saveBook with normal service"() {
        given:
        webRequest.session.setAttribute(SessionTenantResolver.ATTRIBUTE, "moreBooks")

        when:
        Book book = anotherBookService.saveBook("Book-Test-${System.currentTimeMillis()}")
        println book
        log.info("${book}")

        then:
        anotherBookService.countBooks() == 1
        book?.id
    }

    void 'Test database per tenant'() {
        when:"When there is no tenant"
        Book.count()

        then:"You still get an exception"
        thrown(TenantNotFoundException)

        when:"But look you can add a new Schema at runtime!"
        webRequest.session.setAttribute(SessionTenantResolver.ATTRIBUTE, "moreBooks")

        then:
        anotherBookService.countBooks() == 0
        bookService.countBooks()== 0

        when:"And the new @CurrentTenant transformation deals with the details for you!"
        anotherBookService.saveBook("The Stand")
        anotherBookService.saveBook("The Shining")
        anotherBookService.saveBook("It")

        then:
        anotherBookService.countBooks() == 3
        bookService.countBooks()== 3

        when:"Swapping to another schema and we get the right results!"
        webRequest.session.setAttribute(SessionTenantResolver.ATTRIBUTE, "evenMoreBooks")

        anotherBookService.saveBook("Along Came a Spider")
        bookService.saveBook("Whatever")
        then:
        anotherBookService.countBooks() == 2
        bookService.countBooks()== 2
    }
}

