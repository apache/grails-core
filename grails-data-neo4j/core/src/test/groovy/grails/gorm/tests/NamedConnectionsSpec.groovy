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

import grails.gorm.annotation.Entity
import grails.gorm.transactions.Transactional
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
 * A connection declared under {@code grails.neo4j.connections}, against a server of its own, so that where a write
 * lands shows which connection it went through.
 */
class NamedConnectionsSpec extends Specification {

    @Shared
    @AutoCleanup
    ServerControls defaultServer

    @Shared
    @AutoCleanup
    ServerControls otherServer

    @Shared
    @AutoCleanup
    Neo4jDatastore datastore

    private static int findAvailableTcpPort() {
        new ServerSocket(0).withCloseable { it.localPort }
    }

    void setupSpec() {
        int defaultPort = findAvailableTcpPort()
        int otherPort = findAvailableTcpPort()
        defaultServer = EmbeddedNeo4jServer.start('localhost', defaultPort, File.createTempDir())
        otherServer = EmbeddedNeo4jServer.start('localhost', otherPort, File.createTempDir())
        datastore = new Neo4jDatastore([
                'grails.neo4j.options.encryptionLevel': 'NONE',
                (Settings.SETTING_NEO4J_URL)          : "bolt://localhost:${defaultPort}".toString(),
                (Settings.SETTING_NEO4J_BUILD_INDEX)  : false,
                (Settings.SETTING_CONNECTIONS)        : [other: [url: "bolt://localhost:${otherPort}".toString()]]
        ], RoutedCompany)
    }

    void cleanup() {
        RoutedCompany.withTransaction { RoutedCompany.list()*.delete(flush: true) }
        RoutedCompany.other.withTransaction { RoutedCompany.other.list().each { RoutedCompany.other.delete(it, [flush: true]) } }
    }

    void "test an entity is saved through a named connection to that connection's server"() {
        when:
        RoutedCompany.other.withTransaction {
            RoutedCompany.other.save(new RoutedCompany(name: 'Other'), [flush: true])
        }

        then:
        RoutedCompany.other.count() == 1
        RoutedCompany.count() == 0
    }

    void "test the operations that name the class inside withConnection use that connection"() {
        when: "an instance is saved inside the block"
        RoutedCompany.withConnection('other') {
            RoutedCompany.withTransaction {
                new RoutedCompany(name: 'Routed').save(flush: true)
            }
        }

        then: "it reaches that connection's server, not the default one"
        RoutedCompany.other.count() == 1
        RoutedCompany.count() == 0

        and: "a static call on the class inside the block reads from it, and one naming its connection keeps it"
        RoutedCompany.withConnection('other') { RoutedCompany.count() } == 1
        RoutedCompany.withConnection('other') { RoutedCompany.'default'.count() } == 0
    }

    void "test an instance saved inside a named connection's own transaction is written to that connection"() {
        when:
        RoutedCompany.other.withTransaction {
            new RoutedCompany(name: 'In transaction').save(flush: true)
        }

        then: "it reaches that connection's server, not the default one"
        RoutedCompany.other.count() == 1
        RoutedCompany.count() == 0

        and: "the class's own calls inside that connection's session read from it"
        RoutedCompany.other.withNewSession { RoutedCompany.count() } == 1
    }

    void "test the class's own calls inside a method annotated @Transactional with a connection use it"() {
        given:
        def service = new RoutedCompanyService()

        when:
        service.saveCompany('Annotated')

        then: "it reaches that connection's server, and is read back through it"
        RoutedCompany.other.count() == 1
        RoutedCompany.count() == 0
        service.countCompanies() == 1
    }
}

@Transactional(connection = 'other')
class RoutedCompanyService {

    RoutedCompany saveCompany(String name) {
        new RoutedCompany(name: name).save(flush: true)
    }

    Number countCompanies() {
        RoutedCompany.count()
    }
}

@Entity
class RoutedCompany implements Neo4jEntity<RoutedCompany> {
    Long id
    String name

    static mapping = {
        connections ConnectionSource.DEFAULT, 'other'
    }
}
