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

import grails.gorm.DetachedCriteria
import grails.gorm.annotation.Entity
import grails.mongodb.MongoEntity
import org.apache.grails.testing.mongo.AutoStartedMongoSpec
import org.bson.types.ObjectId
import org.grails.datastore.mapping.mongo.MongoDatastore
import org.grails.datastore.mapping.mongo.config.MongoSettings
import spock.lang.Shared

/**
 * A subquery is run by MongoDB before the outer query, so it has to read the connection the outer query reads. The
 * two connections hold different ages, so the average of the wrong connection selects different rows.
 */
class SubqueryConnectionSpec extends AutoStartedMongoSpec {

    @Shared MongoDatastore datastore

    @Override
    boolean shouldInitializeDatastore() {
        false
    }

    void setupSpec() {
        datastore = new MongoDatastore([
                (MongoSettings.SETTING_URL)        : "mongodb://${mongoHost}:${mongoPort}/subqueryDefaultDb" as String,
                (MongoSettings.SETTING_CONNECTIONS): [
                        archive: [url: "mongodb://${mongoHost}:${mongoPort}/subqueryArchiveDb" as String]
                ]
        ], getDomainClasses() as Class[])
    }

    void cleanupSpec() {
        datastore.close()
    }

    void setup() {
        ConnSubPerson.DB.drop()
        ConnSubPerson.archive.DB.drop()
        [['Bart', 10], ['Lisa', 20]].each { new ConnSubPerson(name: it[0], age: it[1]).save(flush: true) }
        [['Abe', 40], ['Mona', 60], ['Jasper', 80]].each {
            ConnSubPerson.archive.save(new ConnSubPerson(name: it[0], age: it[1]), [flush: true])
        }
    }

    void cleanup() {
        ConnSubPerson.DB.drop()
        ConnSubPerson.archive.DB.drop()
    }

    void "an aggregate subquery averages the rows of the connection the query is applied to"() {
        when:
        List<ConnSubPerson> found = ConnSubPerson.where { age > avg(age) }.withConnection('archive').list(sort: 'name')

        then: "the average of the archive is 60, so only Jasper is above it; the default average of 15 would select all three"
        found*.name == ['Jasper']
    }

    void "an aggregate subquery with criteria averages the rows of the connection the query is applied to"() {
        when:
        List<ConnSubPerson> found = ConnSubPerson.where { age >= avg(age).of { age > 50 } }.withConnection('archive').list(sort: 'name')

        then: "the average of the archive ages above 50 is 70, so only Jasper is at least that"
        found*.name == ['Jasper']
    }

    void "a comparison with a subquery on the default connection still reads the default connection"() {
        when:
        List<ConnSubPerson> found = ConnSubPerson.where { age > avg(age) }.list(sort: 'name')

        then:
        found*.name == ['Lisa']
    }

    void "inList against a subquery reads the connection the query is applied to"() {
        when:
        List<ConnSubPerson> found = new DetachedCriteria(ConnSubPerson).withConnection('archive').build {
            inList 'age', ConnSubPerson.where { age > 50 }.property('age')
        }.list(sort: 'name')

        then: "the default connection has no age above 50, so a subquery run there would select nothing"
        found*.name == ['Jasper', 'Mona']
    }

    void "a subquery built with its own connection keeps it"() {
        when:
        List<ConnSubPerson> found = new DetachedCriteria(ConnSubPerson).build {
            gt 'age', ConnSubPerson.where { age > 0 }.withConnection('archive').avg('age')
        }.list(sort: 'name')

        then: "the archive average is 60, so on the default rows nothing is above it"
        found.isEmpty()
    }

    List getDomainClasses() {
        [ConnSubPerson]
    }
}

@Entity
class ConnSubPerson implements MongoEntity<ConnSubPerson> {
    ObjectId id
    String name
    Integer age

    static mapping = {
        connections 'default', 'archive'
    }
}
