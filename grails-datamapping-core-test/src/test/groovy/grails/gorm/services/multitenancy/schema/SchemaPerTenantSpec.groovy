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
package grails.gorm.services.multitenancy.schema

import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity
import grails.gorm.multitenancy.CurrentTenant
import grails.gorm.services.Service
import grails.gorm.transactions.ReadOnly
import grails.gorm.transactions.Transactional
import org.grails.datastore.mapping.config.Settings
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.multitenancy.exceptions.TenantNotFoundException
import org.grails.datastore.mapping.multitenancy.resolvers.SystemPropertyTenantResolver
import org.grails.datastore.mapping.simple.SimpleMapDatastore
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class SchemaPerTenantSpec extends Specification {

    @Shared @AutoCleanup SimpleMapDatastore datastore = new SimpleMapDatastore(
            [(Settings.SETTING_MULTI_TENANCY_MODE): MultiTenancySettings.MultiTenancyMode.SCHEMA,
             (Settings.SETTING_MULTI_TENANT_RESOLVER): new SystemPropertyTenantResolver(),
             (Settings.SETTING_DB_CREATE): "create-drop"],
            getClass().getPackage()
    )
    @Shared IBookService bookDataService = datastore.getService(IBookService)

    def setup() {
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "")
    }

    void 'Test schema per tenant'() {
        when:"When there is no tenant"
        Book.count()

        then:"You still get an exception"
        thrown(TenantNotFoundException)

        when:"But look you can add a new Schema at runtime!"
        datastore.addTenantForSchema('foo')
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "foo")

        BookService bookService = new BookService()

        then:
        bookService.countBooks() == 0
        bookDataService.countBooks()== 0

        when:"And the new @CurrentTenant transformation deals with the details for you!"
        bookService.saveBook("The Stand")

        then:
        bookService.countBooks() == 1
        bookDataService.countBooks()== 1

        when:"Swapping to another schema and we get the right results!"
        datastore.addTenantForSchema('bar')
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "bar")

        then:
        bookService.countBooks() == 0
        bookDataService.countBooks()== 0
    }
}

@Entity
class Book implements MultiTenant<Book> {
    String title
}

@CurrentTenant
@Transactional
class BookService {

    void saveBook(String title) {
        new Book(title:"The Stand").save()
    }

    @ReadOnly
    int countBooks() {
        Book.count()
    }
}

@CurrentTenant
@Service(Book)
interface IBookService {

    Book saveBook(String title)

    Integer countBooks()
}