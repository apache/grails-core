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
package grails.gorm.tests

import grails.gorm.DetachedCriteria
import grails.gorm.annotation.Entity
import grails.neo4j.Neo4jEntity
import org.neo4j.harness.ServerControls
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import org.grails.datastore.gorm.neo4j.Neo4jDatastore
import org.grails.datastore.gorm.neo4j.config.Settings
import org.grails.datastore.gorm.neo4j.util.EmbeddedNeo4jServer
import org.grails.datastore.mapping.core.connections.ConnectionSource

/**
 * A subquery is run by Neo4j before the outer query, so it has to read the connection the outer query reads. The
 * two connections hold different ages, so the average of the wrong connection selects different rows.
 */
class SubqueryConnectionSpec extends Specification {

    @Shared
    @AutoCleanup
    ServerControls defaultServer

    @Shared
    @AutoCleanup
    ServerControls archiveServer

    @Shared
    @AutoCleanup
    Neo4jDatastore datastore

    private static int findAvailableTcpPort() {
        new ServerSocket(0).withCloseable { it.localPort }
    }

    void setupSpec() {
        int defaultPort = findAvailableTcpPort()
        int archivePort = findAvailableTcpPort()
        defaultServer = EmbeddedNeo4jServer.start('localhost', defaultPort, File.createTempDir())
        archiveServer = EmbeddedNeo4jServer.start('localhost', archivePort, File.createTempDir())
        datastore = new Neo4jDatastore([
                'grails.neo4j.options.encryptionLevel': 'NONE',
                (Settings.SETTING_NEO4J_URL)          : "bolt://localhost:${defaultPort}".toString(),
                (Settings.SETTING_NEO4J_BUILD_INDEX)  : false,
                (Settings.SETTING_CONNECTIONS)        : [archive: [url: "bolt://localhost:${archivePort}".toString()]]
        ], ConnSubPerson)
    }

    void setup() {
        ConnSubPerson.withTransaction {
            [['Bart', 10], ['Lisa', 20]].each { new ConnSubPerson(name: it[0], age: it[1]).save(flush: true) }
        }
        ConnSubPerson.archive.withTransaction {
            [['Abe', 40], ['Mona', 60], ['Jasper', 80]].each {
                ConnSubPerson.archive.save(new ConnSubPerson(name: it[0], age: it[1]), [flush: true])
            }
        }
    }

    void cleanup() {
        ConnSubPerson.withTransaction { ConnSubPerson.list()*.delete(flush: true) }
        ConnSubPerson.archive.withTransaction {
            ConnSubPerson.archive.list().each { ConnSubPerson.archive.delete(it, [flush: true]) }
        }
    }

    private static List<String> namesOn(String connection, Closure<List<ConnSubPerson>> query) {
        Closure<List<String>> read = { query.call()*.name }
        connection == ConnectionSource.DEFAULT ? ConnSubPerson.withTransaction(read) : ConnSubPerson."$connection".withTransaction(read)
    }

    void "an aggregate subquery averages the rows of the connection the query is applied to"() {
        when:
        List<String> found = namesOn('archive') { ConnSubPerson.where { age > avg(age) }.withConnection('archive').list(sort: 'name') }

        then: "the average of the archive is 60, so only Jasper is above it; the default average of 15 would select all three"
        found == ['Jasper']
    }

    void "an aggregate subquery with criteria averages the rows of the connection the query is applied to"() {
        when:
        List<String> found = namesOn('archive') { ConnSubPerson.where { age >= avg(age).of { age > 50 } }.withConnection('archive').list(sort: 'name') }

        then: "the average of the archive ages above 50 is 70, so only Jasper is at least that"
        found == ['Jasper']
    }

    void "a comparison with a subquery on the default connection still reads the default connection"() {
        when:
        List<String> found = namesOn(ConnectionSource.DEFAULT) { ConnSubPerson.where { age > avg(age) }.list(sort: 'name') }

        then:
        found == ['Lisa']
    }

    void "inList against a subquery reads the connection the query is applied to"() {
        when:
        List<String> found = namesOn('archive') {
            new DetachedCriteria(ConnSubPerson).withConnection('archive').build {
                inList 'age', ConnSubPerson.where { age > 50 }.property('age')
            }.list(sort: 'name')
        }

        then: "the default connection has no age above 50, so a subquery run there would select nothing"
        found == ['Jasper', 'Mona']
    }

    void "a subquery built with its own connection keeps it"() {
        when:
        List<String> found = namesOn(ConnectionSource.DEFAULT) {
            new DetachedCriteria(ConnSubPerson).build {
                gt 'age', ConnSubPerson.where { age > 0 }.withConnection('archive').avg('age')
            }.list(sort: 'name')
        }

        then: "the archive average is 60, so on the default rows nothing is above it"
        found.isEmpty()
    }
}

@Entity
class ConnSubPerson implements Neo4jEntity<ConnSubPerson> {
    Long id
    String name
    Integer age

    static mapping = {
        connections ConnectionSource.DEFAULT, 'archive'
    }
}
