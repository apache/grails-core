package org.grails.datastore.gorm.mongo

import grails.gorm.annotation.Entity
import grails.gorm.tests.GormDatastoreSpec
import grails.mongodb.MongoEntity
import org.bson.types.ObjectId
import org.grails.datastore.mapping.mongo.MongoDatastore
import org.grails.datastore.mapping.mongo.config.MongoSettings
import spock.lang.Shared
import spock.lang.Specification

/**
 * Created by graemerocher on 30/06/16.
 */
class MultipleConnectionsSpec extends Specification {

    @Shared MongoDatastore datastore

    void setupSpec() {
        Map config = [
            (MongoSettings.SETTING_URL)        : "mongodb://localhost/defaultDb",
            (MongoSettings.SETTING_CONNECTIONS): [
                    test1: [
                            url: "mongodb://localhost/test1Db"
                    ],
                    test2: [
                            url: "mongodb://localhost/test2Db"
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

    List getDomainClasses() {
        [CompanyA]
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

