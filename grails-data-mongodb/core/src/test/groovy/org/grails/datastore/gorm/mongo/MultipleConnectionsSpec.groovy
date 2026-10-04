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
package org.grails.datastore.gorm.mongo

import grails.gorm.annotation.Entity
import grails.gorm.transactions.Transactional
import grails.mongodb.MongoEntity
import org.apache.grails.testing.mongo.AutoStartedMongoSpec
import org.bson.types.ObjectId
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.mongo.MongoDatastore
import org.grails.datastore.mapping.mongo.config.MongoSettings
import spock.lang.Shared

/**
 * Created by graemerocher on 30/06/16.
 */
class MultipleConnectionsSpec extends AutoStartedMongoSpec {

    @Shared MongoDatastore datastore

    @Override
    boolean shouldInitializeDatastore() {
        false
    }

    void setupSpec() {
        Map config = [
            (MongoSettings.SETTING_URL)        : "mongodb://${mongoHost}:${mongoPort}/defaultDb" as String,
            (MongoSettings.SETTING_CONNECTIONS): [
                    test1: [
                            url: "mongodb://${mongoHost}:${mongoPort}/test1Db" as String
                    ],
                    test2: [
                            url: "mongodb://${mongoHost}:${mongoPort}/test2Db" as String
                    ]
            ]
        ]
        this.datastore = new MongoDatastore(config, getDomainClasses() as Class[])
    }

    void cleanupSpec() {
        datastore.close()
    }

    void "Test multiple datasources state"() {

        expect:
        CompanyA.DB.name == 'test1Db'
        CompanyA.test2.DB.name == 'test2Db'
    }

    void "Test query multiple data sources"() {
        setup:
        CompanyA.DB.drop()
        CompanyA.test2.DB.drop()

        when:"An entity is saved"
        new CompanyA(name:"One").save(flush:true)

        then:"The results are correct"
        CompanyA.count() == 1
        CompanyA.withConnection("test2") { count() } == 0

        when:"An entity is saved to another connection"
        new CompanyA(name:"Two").save(flush:true)
        CompanyA.withConnection("test2") {
            save(new CompanyA(name: "Three"), [flush:true])
        }

        then:"The results are correct"
        CompanyA.count() == 2
        CompanyA.first()
        CompanyA.withConnection("test2") { count() == 1 }
    }

    void "Test instance operations reached through the named-connection static api"() {
        setup:
        CompanyA.DB.drop()
        CompanyA.test2.DB.drop()

        when:"An instance operation is reached through the named-connection static api itself"
        def company = new CompanyA(name: "Four")
        CompanyA.test2.save(company, [flush: true])

        then:"It is written to that connection and not to the entity's own default one"
        company.id != null
        CompanyA.test2.count() == 1
        CompanyA.count() == 0

        when:"The same handle deletes it"
        CompanyA.test2.delete(company, [flush: true])

        then:
        CompanyA.test2.count() == 0

        cleanup:
        CompanyA.DB.drop()
        CompanyA.test2.DB.drop()
    }

    void "Test instance operations stay on the entity's own connection by default"() {
        setup:
        CompanyA.DB.drop()
        CompanyA.test2.DB.drop()

        when:"An instance is saved the ordinary way"
        new CompanyA(name: "Five").save(flush: true)

        then:"It lands on the entity's first mapped connection, and nothing reaches the other one"
        CompanyA.count() == 1
        CompanyA.test2.count() == 0

        cleanup:
        CompanyA.DB.drop()
        CompanyA.test2.DB.drop()
    }

    void "Test the operations that name the class inside withConnection use that connection"() {
        setup:
        ScopedCompany.DB.drop()
        ScopedCompany.test2.DB.drop()

        when: "an instance is saved inside the block, as the guide shows it"
        ScopedCompany.withConnection("test2") {
            new ScopedCompany(name: "Six").save(flush: true)
        }

        then: "it reaches that connection, not the entity's default one"
        ScopedCompany.test2.count() == 1
        ScopedCompany.count() == 0

        and: "static calls on the class, finders and queries inside the block read from it"
        ScopedCompany.withConnection("test2") { ScopedCompany.count() } == 1
        ScopedCompany.withConnection("test2") { ScopedCompany.list()*.name } == ["Six"]
        ScopedCompany.withConnection("test2") { ScopedCompany.findByName("Six")?.name } == "Six"
        ScopedCompany.withConnection("test2") { ScopedCompany.where { name == "Six" }.count() } == 1

        cleanup:
        ScopedCompany.DB.drop()
        ScopedCompany.test2.DB.drop()
    }

    void "Test withConnection leaves an operation that names its connection, and later calls, alone"() {
        setup:
        ScopedCompany.DB.drop()
        ScopedCompany.test2.DB.drop()
        new ScopedCompany(name: "Seven").save(flush: true)

        expect: "a connection named inside the block is the one used"
        ScopedCompany.withConnection("test2") {
            [named: ScopedCompany.'default'.count(), scoped: ScopedCompany.count()]
        } == [named: 1, scoped: 0]

        and: "after the block the entity is back on its default connection"
        ScopedCompany.count() == 1

        when: "a block fails"
        ScopedCompany.withConnection("test2") { throw new IllegalStateException("failed inside the block") }

        then: "the default connection applies again after it too"
        thrown(IllegalStateException)
        ScopedCompany.count() == 1

        cleanup:
        ScopedCompany.DB.drop()
        ScopedCompany.test2.DB.drop()
    }

    void "Test an instance saved inside a named connection's own session or transaction is written to that connection"() {
        setup:
        ScopedCompany.DB.drop()
        ScopedCompany.test2.DB.drop()

        when:
        ScopedCompany.test2.withNewSession {
            new ScopedCompany(name: "In session").save(flush: true)
        }
        ScopedCompany.test2.withTransaction {
            new ScopedCompany(name: "In transaction").save(flush: true)
        }

        then: "both are in that connection's database, not the entity's default one"
        ScopedCompany.test2.count() == 2
        ScopedCompany.count() == 0

        and: "the class's own calls inside that connection's session read from it"
        ScopedCompany.test2.withNewSession { ScopedCompany.list()*.name.sort() } == ["In session", "In transaction"]

        cleanup:
        ScopedCompany.DB.drop()
        ScopedCompany.test2.DB.drop()
    }

    void "Test the class's own calls inside a named connection's stateless session read from it"() {
        setup:
        ScopedCompany.DB.drop()
        ScopedCompany.test2.DB.drop()
        ScopedCompany.test2.save(new ScopedCompany(name: "Stateless"), [flush: true])

        expect:
        ScopedCompany.test2.withStatelessSession { ScopedCompany.count() } == 1
        ScopedCompany.count() == 0

        cleanup:
        ScopedCompany.DB.drop()
        ScopedCompany.test2.DB.drop()
    }

    void "Test a class a withConnection block does not name keeps its own connection"() {
        setup:
        BystanderCompany.DB.drop()
        BystanderCompany.test2.DB.drop()

        when: "a class the block does not name is saved inside a block for another one"
        CompanyA.withConnection("test2") {
            new BystanderCompany(name: "Bystander").save(flush: true)
        }

        then: "it is written to its own first connection, not the block's"
        BystanderCompany.count() == 1
        BystanderCompany.test2.count() == 0

        cleanup:
        BystanderCompany.DB.drop()
        BystanderCompany.test2.DB.drop()
    }

    void "Test the class's own calls inside a method annotated @Transactional with a connection use it"() {
        setup:
        ScopedCompany.DB.drop()
        ScopedCompany.test2.DB.drop()
        def service = new ScopedCompanyService()

        when:
        service.saveCompany("Eight")

        then: "it is written to that connection, and read back through it"
        ScopedCompany.test2.count() == 1
        ScopedCompany.count() == 0
        service.countCompanies() == 1

        cleanup:
        ScopedCompany.DB.drop()
        ScopedCompany.test2.DB.drop()
    }

    List getDomainClasses() {
        [CompanyA, ScopedCompany, BystanderCompany]
    }
}

/**
 * Created by graemerocher on 30/06/16.
 */
@Entity
class CompanyA implements MongoEntity<CompanyA> {
    ObjectId id
    String name
    static mapping = {
        connections "test1", "test2"
    }
}

@Transactional(connection = "test2")
class ScopedCompanyService {

    ScopedCompany saveCompany(String name) {
        new ScopedCompany(name: name).save(flush: true)
    }

    Number countCompanies() {
        ScopedCompany.count()
    }
}

@Entity
class BystanderCompany implements MongoEntity<BystanderCompany> {
    ObjectId id
    String name
    static mapping = {
        connections "test1", "test2"
    }
}

@Entity
class ScopedCompany implements MongoEntity<ScopedCompany> {
    ObjectId id
    String name
    static mapping = {
        connections ConnectionSource.DEFAULT, "test2"
    }
}

