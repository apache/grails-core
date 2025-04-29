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
package org.grails.datastore.gorm.mongo.multitenancy

import com.mongodb.client.model.Filters
import grails.gorm.MultiTenant
import grails.mongodb.MongoEntity
import grails.persistence.Entity
import org.apache.grails.testing.mongo.AutoStartedMongoSpec
import org.bson.types.ObjectId
import org.grails.datastore.mapping.mongo.MongoDatastore
import org.grails.datastore.mapping.mongo.config.MongoSettings
import org.grails.datastore.mapping.multitenancy.exceptions.TenantNotFoundException
import org.grails.datastore.mapping.multitenancy.resolvers.SystemPropertyTenantResolver
import spock.lang.AutoCleanup
import spock.lang.Shared

class MongoStaticApiMultiTenancySpec extends AutoStartedMongoSpec {

    @Shared  @AutoCleanup MongoDatastore datastore

    @Override
    boolean shouldInitializeDatastore() {
        false
    }

    void setupSpec() {
        Map config = [
                "grails.gorm.multiTenancy.mode"               : "DISCRIMINATOR",
                "grails.gorm.multiTenancy.tenantResolverClass": SystemPropertyTenantResolver,
                (MongoSettings.SETTING_URL)                   : "mongodb://${mongoHost}:${mongoPort}/defaultDb" as String,
        ]
        this.datastore = new MongoDatastore(config, getDomainClasses() as Class[])
    }

    void setup() {
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "")
    }


    void "test search"() {
        setup: "drop existing database"
        Book.DB.drop()
        datastore.buildIndex()

        when: "no book exists, now search for a book"
        Book.search("Grails")

        then: "not able to resolve tenantId"
        thrown(TenantNotFoundException)

        when: "set tenantId, and create two books"
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "mix")
        createBook("Grails 3 - Step by Step")
        createBook("Making Java Groovy")

        and: "search for book"
        List result = Book.search("Grails")

        then: "should find the only book"
        result.size() == 1

        when: "change the tenantId"
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "others")

        and: "search for book again"
        result = Book.search("Grails")

        then: "should not find the book"
        result == []

        when: "change the tenantId, and create some books"
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "grails")
        createBooks()

        and: "search for the book"
        result = Book.search("grails")

        then: "should find 6 books"
        result.size() == 6

    }

    void "test searchTop"() {
        setup: "drop existing database"
        Book.DB.drop()
        datastore.buildIndex()

        when: "no book exists, now search for a book"
        Book.searchTop("Grails")

        then: "not able to resolve tenantId"
        thrown(TenantNotFoundException)

        when: "set tenantId, and create two books"
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "mix")
        createBook("Grails 3 - Step by Step")
        createBook("Making Java Groovy")

        and: "search for the book"
        List<Book> result = Book.searchTop("Grails 3")

        then: "should return the only book"
        result.size() == 1

        when: "change the tenantId"
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "others")

        and: "search for book again"
        result = Book.searchTop("Grails")

        then: "should not find the book"
        result == []

        when: "change the tenantId, and create some books"
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "grails")
        createBooks()

        and: "search for the book"
        result = Book.searchTop("grails")

        then: "should find only 5 books by default"
        result.size() == 5

        when: "search for the book with max"
        result = Book.searchTop("grails", 10)

        then: "should find 6 books"
        result.size() == 6
    }


    void "test find"() {
        setup: "drop existing database"
        Book.DB.drop()
        datastore.buildIndex()

        when: "no book exists, now search for a book"
        Book.searchTop("Grails")

        then: "not able to resolve tenantId"
        thrown(TenantNotFoundException)

        when: "set tenantId, and create two books"
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "mix")
        createBook("Grails 3 - Step by Step")
        createBook("Making Java Groovy")

        and: "search for the book"
        List<Book> result = Book.find(Filters.eq("title","Grails 3 - Step by Step")).toList()

        then: "should return the only book"
        result.size() == 1

        when: "change the tenantId"
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "others")

        and: "search for book again"
        result = Book.find(Filters.eq("title", "Grails 3 - Step by Step")).toList()

        then: "should not find the book"
        result == []

        when: "change the tenantId, and create some books"
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "grails")
        createBooks()

        and: "search for the book"
        result = Book.find(Filters.eq("title", "Grails 3 - Step by Step")).toList()

        then: "should find the book"
        result.size() == 1
    }


    void "test count"() {
        setup: "drop existing database"
        Book.DB.drop()
        datastore.buildIndex()

        when: "no book exists, now search for a book"
        Book.count(Filters.eq("title","Grails 3 - Step by Step"))

        then: "not able to resolve tenantId"
        thrown(TenantNotFoundException)

        when: "set tenantId, and create two books"
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "mix")
        createBook("Grails 3 - Step by Step")
        createBook("Making Java Groovy")

        and: "search for the book"
        Integer count = Book.count(Filters.eq("title","Grails 3 - Step by Step"))

        then: "should return the only book"
        count == 1

        when: "change the tenantId"
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "others")

        and: "search for book again"
        count = Book.count(Filters.eq("title", "Grails 3 - Step by Step"))

        then: "should not find the book"
        count == 0

        when: "change the tenantId, and create some books"
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "grails")
        createBooks()

        and: "search for the book"
        count = Book.count(Filters.eq("title", "Grails 3 - Step by Step"))

        then: "should find the only book"
        count == 1
    }

    void "test aggregate"() {
        setup: "drop existing database"
        Book.DB.drop()
        datastore.buildIndex()

        when: "no book exists, now search for a book"
        Book.aggregate([['$match': ["title": "Grails 3 - Step by Step"]]])

        then: "not able to resolve tenantId"
        thrown(TenantNotFoundException)

        when: "set tenantId, and create two books"
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "mix")
        createBook("Grails 3 - Step by Step")
        createBook("Making Java Groovy")

        and: "search for the book"
        List result = Book.aggregate([['$match': ["title": "Grails 3 - Step by Step"]]])

        then: "should return the only book"
        result.size() == 1

        when: "change the tenantId"
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "others")

        and: "search for book again"
        result = Book.aggregate([['$match': ["title": "Grails 3 - Step by Step"]]])

        then: "should not find the book"
        result.size() == 0

        when: "change the tenantId, and create some books"
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "grails")
        createBooks()

        and: "search for the book"
        result = Book.aggregate([['$match': ["title": "Grails 3 - Step by Step"]]])

        then: "should find the only book"
        result.size() == 1
    }

    List getDomainClasses() {
        [Book]
    }

    static Book createBook(String title) {
        new Book(title: title).save(flush: true)
    }

    static void createBooks() {
        ["Grails Goodness Notebook",
         "Falando de Grails",
         "The Definitive Guide to Grails 2",
         "Grails 3 - Step by Step",
         "Making Java Groovy",
         "Grails in Action", "Practical Grails 3"
        ].each { String title ->
            createBook(title)
        }
    }

}

@Entity
class Book implements MultiTenant<Book>, MongoEntity<Book> {
    ObjectId id
    String tenantId
    String title

    static mapping = {
        index title:"text"
    }
}
