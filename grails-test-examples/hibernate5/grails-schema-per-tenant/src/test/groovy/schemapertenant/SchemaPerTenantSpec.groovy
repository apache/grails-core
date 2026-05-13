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

import grails.gorm.transactions.Rollback
import grails.test.hibernate.HibernateSpec
import org.grails.datastore.mapping.config.Settings
import org.grails.datastore.mapping.multitenancy.exceptions.TenantNotFoundException
import org.grails.datastore.mapping.multitenancy.resolvers.SystemPropertyTenantResolver
import org.grails.testing.GrailsUnitTest
import spock.util.environment.RestoreSystemProperties

/**
 * Created by graemerocher on 06/04/2017.
 */
@RestoreSystemProperties
class SchemaPerTenantSpec extends HibernateSpec implements GrailsUnitTest {

    @Override
    List<Class> getDomainClasses() { [Book] }


    BookService bookDataService = hibernateDatastore.getService(BookService)

    @Override
    Map getConfiguration() {
        Collections.unmodifiableMap(
                (Settings.SETTING_MULTI_TENANT_RESOLVER): new SystemPropertyTenantResolver(),
                (Settings.SETTING_DB_CREATE): "create-drop"
        )
    }

    def setup() {
        //To register MimeTypes
        if (grailsApplication.mainContext.parent) {
            grailsApplication.mainContext.getBean("mimeTypesHolder")
        }
        hibernateDatastore.addTenantForSchema("moreBooks")
        hibernateDatastore.addTenantForSchema("evenMoreBooks")
    }

    @Rollback("moreBooks")
    void "Test should rollback changes in a previous test"() {
        when:"When there is no tenant"
        Book.count()

        then:"No tenant-scoped records are visible"
        Book.count() == 0

        when:"You can save a book"

        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "moreBooks")
        bookDataService.saveBook("The Stand")

        then:"And the changes will be rolled back for the next test"
        bookDataService.countBooks() == 1
    }

    void 'Test database per tenant'() {
        when:"But look you can add a new Schema at runtime!"

        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "moreBooks")

        AnotherBookService bookService = new AnotherBookService()
        bookService.setTargetDatastore(hibernateDatastore)
        bookService.setTransactionManager(transactionManager)

        then:
        bookService.countBooks() == 0
        bookDataService.countBooks()== 0

        when:"And the new @CurrentTenant transformation deals with the details for you!"
        bookService.saveBook("The Stand")
        bookService.saveBook("The Shining")
        bookService.saveBook("It")

        then:
        bookService.countBooks() == 3
        bookDataService.countBooks()== 3

    }
}
