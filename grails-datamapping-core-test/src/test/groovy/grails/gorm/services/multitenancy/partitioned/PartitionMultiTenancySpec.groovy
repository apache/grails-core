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
package grails.gorm.services.multitenancy.partitioned

import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity
import grails.gorm.multitenancy.CurrentTenant
import grails.gorm.multitenancy.WithoutTenant
import grails.gorm.services.Service
import grails.gorm.transactions.ReadOnly
import grails.gorm.transactions.Transactional
import org.grails.datastore.mapping.config.Settings
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.multitenancy.exceptions.TenantNotFoundException
import org.grails.datastore.mapping.multitenancy.resolvers.SystemPropertyTenantResolver
import org.grails.datastore.mapping.simple.SimpleMapDatastore

@RestoreSystemProperties
class PartitionMultiTenancySpec extends Specification {

    @Shared
    @AutoCleanup
    SimpleMapDatastore datastore = new SimpleMapDatastore(
            [(Settings.SETTING_MULTI_TENANCY_MODE): MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR,
             (Settings.SETTING_MULTI_TENANT_RESOLVER): new SystemPropertyTenantResolver(),
             (Settings.SETTING_DB_CREATE)         : "create-drop"],
            getClass().getPackage()
    )
    @Shared
    IBookService bookDataService = datastore.getService(IBookService)

    void 'test a block for a tenant id, which names no connection, leaves the routing alone'() {
        given: 'two books for one tenant and one for another'
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, '900')
        Book.withTransaction {
            new Book(title: 'First').save(flush: true)
            new Book(title: 'Second').save(flush: true)
        }
        Book.withTenant('901') {
            Book.withTransaction { new Book(title: 'Another tenant').save(flush: true) }
        }

        expect: 'a session or transaction opened for a tenant leaves the calls on the class to the current tenant'
        Book.withTenant('901').withTransaction { Book.count() } == 2
        Book.withTenant('901').withNewSession { Book.count() } == 2

        and: 'while the call that names the tenant reaches it'
        Book.withTenant('901').count() == 1

        cleanup:
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, '')
    }

    void 'an instance whose tenant id names another tenant is saved under the current tenant'() {
        given: 'a current tenant'
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, '910')

        when: 'a book with the tenant id of another tenant is saved'
        Book book = Book.withTransaction { new Book(title: 'Inserted', tenantId: 911).save(flush: true) }

        then: 'it gets the current tenant, and only the current tenant sees it'
        book.tenantId == 910
        Book.withTransaction { Book.countByTitle('Inserted') } == 1
        Book.withTenant('911') { Book.withTransaction { Book.countByTitle('Inserted') } } == 0

        when: 'its tenant id is changed to another tenant and it is saved again'
        Book updated = Book.withTransaction {
            Book loaded = Book.get(book.id)
            loaded.tenantId = 911
            loaded.save(flush: true)
        }

        then: 'it keeps the current tenant, and only the current tenant sees it'
        updated.tenantId == 910
        Book.withTransaction { Book.countByTitle('Inserted') } == 1
        Book.withTenant('911') { Book.withTransaction { Book.countByTitle('Inserted') } } == 0

        cleanup:
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, '')
    }

    void 'saving without a tenant throws the TenantNotFoundException of the tenant resolver'() {
        given: 'no current tenant'
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, '')

        when: 'a book is saved'
        Book.withTransaction { new Book(title: 'No tenant').save(flush: true) }

        then: 'the exception says that no tenant was found'
        thrown(TenantNotFoundException)
    }

    void 'Test partitioned multi-tenancy with GORM services'() {
        setup:
        BookService bookService = new BookService()

        when: "When there is no tenant"
        Book.count()

        then: "You still get an exception"
        thrown(TenantNotFoundException)

        when: "But look you can change tenant"
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "12")

        then:
        bookService.countBooks() == 0
        bookDataService.countBooks() == 0

        when: "And the new @CurrentTenant transformation deals with the details for you!"
        bookService.saveBook("The Stand")
        bookService.saveBook("The Shining")

        then:
        bookService.countBooks() == 2
        bookDataService.countBooks() == 2
        bookService.findBooks("The Shining")[0].title == "The Shining"

        when: "Swapping to another schema and we get the right results!"
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "13")

        then:
        bookService.countBooks() == 0
        bookDataService.countBooks() == 0

        when: "calling a method save using Tenants.withoutId"
        Book book1 = new Book(title: "The Secret", tenantId: 55)
        Book book2 = new Book(title: "The Secret - 2", tenantId: 55)

        bookDataService.saveBook(book1)
        bookDataService.saveBook(book2)

        and: "Swapping to another schema and we get the right results!"
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "55")

        then: "two books are created with tenantId 55"
        bookService.countBooks() == 2
        bookDataService.countBooks() == 2


        when: "book is saved without tenantId"
        Book book3 = bookDataService.saveBook(
                new Book(title: "The Road Trip")
        )

        then: "new book is saved without tenantId"
        book3.id && !book3.tenantId


    }
}

@Entity
class Book implements MultiTenant<Book> {

    Long tenantId
    String title
}

@CurrentTenant
@Transactional
class BookService {

    @WithoutTenant
    void saveBook(Book book) {
        book.save()
    }

    void saveBook(String title) {
        new Book(title: title).save()
    }

    @ReadOnly
    int countBooks() {
        Book.count()
    }

    @ReadOnly
    List<Book> findBooks(String title) {
        (List<Book>) Book.withCriteria {
            eq('title', title)
        }
    }

}

@CurrentTenant
@Service(Book)
interface IBookService {

    Book saveBook(String title)

    @WithoutTenant
    Book saveBook(Book book)

    Integer countBooks()
}