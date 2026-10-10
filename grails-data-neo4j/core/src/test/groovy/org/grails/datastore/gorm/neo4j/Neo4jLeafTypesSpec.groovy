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
package org.grails.datastore.gorm.neo4j

import org.neo4j.driver.Driver
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment
import spock.lang.Specification

import grails.gorm.annotation.Entity
import org.grails.datastore.gorm.neo4j.config.Settings
import org.grails.datastore.gorm.neo4j.connections.Neo4jConnectionSourceFactory
import org.grails.datastore.gorm.neo4j.connections.Neo4jConnectionSourceSettings
import org.grails.datastore.gorm.neo4j.mapping.config.DynamicAssociation
import org.grails.datastore.gorm.neo4j.mapping.config.DynamicToManyAssociation
import org.grails.datastore.gorm.neo4j.mapping.config.DynamicToOneAssociation
import org.grails.datastore.gorm.neo4j.util.IteratorUtil
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.core.connections.DefaultConnectionSource
import org.grails.datastore.mapping.model.PersistentEntity

class Neo4jLeafTypesSpec extends Specification {

    void "type direction pairs compare on type and direction only"() {
        given:
        TypeDirectionPair outgoing = new TypeDirectionPair('KNOWS', true)
        TypeDirectionPair incoming = new TypeDirectionPair('KNOWS', false)
        TypeDirectionPair same = new TypeDirectionPair('KNOWS', true)

        expect:
        outgoing.type == 'KNOWS'
        outgoing.outgoing
        !incoming.outgoing
        outgoing == same
        outgoing.hashCode() == same.hashCode()
        outgoing != incoming
        outgoing != new TypeDirectionPair('LIKES', true)
        outgoing != null
        !outgoing.equals('KNOWS')
        outgoing.toString() == "TypeDirectionPair{type='KNOWS', outgoing=true}"
        outgoing.targetType == null

        when:
        same.targetType = 'Person'
        same.type = 'LIKES'
        same.outgoing = false

        then:
        same.targetType == 'Person'
        same.type == 'LIKES'
        !same.outgoing
        same != outgoing
    }

    void "iterator util returns single values and counts"() {
        expect:
        IteratorUtil.singleOrNull(['a', 'b']) == 'a'
        IteratorUtil.singleOrNull([]) == null
        IteratorUtil.single(['x']) == 'x'
        IteratorUtil.single([].iterator()) == null
        IteratorUtil.count(['a', 'b', 'c']) == 3
        IteratorUtil.count([].iterator()) == 0
    }

    void "settings expose the neo4j configuration keys"() {
        expect:
        Settings.PREFIX == 'grails.neo4j'
        Settings.DEFAULT_URL == 'bolt://localhost:7687'
        Settings.DEFAULT_LOCATION == 'data/neo4j'
        Settings.SETTING_CONNECTIONS == 'grails.neo4j.connections'
        Settings.SETTING_NEO4J_URL == 'grails.neo4j.url'
        Settings.SETTING_NEO4J_BUILD_INDEX == 'grails.neo4j.buildIndex'
        Settings.SETTING_NEO4J_LOCATION == 'grails.neo4j.location'
        Settings.SETTING_NEO4J_TYPE == 'grails.neo4j.type'
        Settings.SETTING_NEO4J_FLUSH_MODE == 'grails.neo4j.flush.mode'
        Settings.SETTING_NEO4J_USERNAME == 'grails.neo4j.username'
        Settings.SETTING_NEO4J_PASSWORD == 'grails.neo4j.password'
        Settings.SETTING_NEO4J_DRIVER_PROPERTIES == 'grails.neo4j.options'
        Settings.SETTING_NEO4J_EMBEDDED_DB_PROPERTIES == 'grails.neo4j.embedded.options'
        Settings.SETTING_NEO4J_EMBEDDED_EPHEMERAL == 'grails.neo4j.embedded.ephemeral'
        Settings.DEFAULT_DATABASE_TYPE == 'remote'
        Settings.DATABASE_TYPE_EMBEDDED == 'embedded'
        Settings.SETTING_DEFAULT_MAPPING == 'grails.neo4j.default.mapping'
        Settings.SETTING_AUTO_FLUSH == 'grails.gorm.autoFlush'
        IdGenerator.Type.values()*.name() == ['NATIVE', 'ASSIGNED', 'SNOWFLAKE', 'CUSTOM']
    }

    void "session flushed events use the datastore as their source"() {
        given:
        Datastore datastore = Stub(Datastore)
        Session session = Stub(Session) { getDatastore() >> datastore }

        when:
        SessionFlushedEvent event = new SessionFlushedEvent(session)

        then:
        event.source.is(datastore)
        event.session.is(session)
    }

    void "dynamic associations point at their associated entity and own the relationship"() {
        given:
        Neo4jMappingContext context = new Neo4jMappingContext(new Neo4jConnectionSourceSettings())
        PersistentEntity owner = context.addPersistentEntity(NltOwner)
        PersistentEntity other = context.addPersistentEntity(NltOther)

        when:
        DynamicToOneAssociation toOne = new DynamicToOneAssociation(owner, context, 'friend', other)
        DynamicToOneAssociation sameToOne = new DynamicToOneAssociation(owner, context, 'friend', other)
        DynamicToManyAssociation toMany = new DynamicToManyAssociation(owner, context, 'friends', other)

        then:
        toOne instanceof DynamicAssociation
        toMany instanceof DynamicAssociation
        toOne.name == 'friend'
        toOne.type == NltOther
        toOne.associatedEntity.is(other)
        toOne.owningSide
        toOne.mapping == null
        toOne == sameToOne
        toOne.hashCode() == sameToOne.hashCode()
        toOne != new DynamicToOneAssociation(owner, context, 'rival', other)
        toOne != new DynamicToOneAssociation(other, context, 'friend', other)
        !toOne.equals(toMany)
        toMany.name == 'friends'
        toMany.associatedEntity.is(other)
        toMany.owningSide
        toMany.mapping == null
    }

    void "the connection source factory builds remote drivers from configuration"() {
        given:
        Neo4jConnectionSourceFactory factory = new Neo4jConnectionSourceFactory()
        StandardEnvironment environment = new StandardEnvironment()
        environment.propertySources.addFirst(new MapPropertySource('test', [
                'grails.neo4j.url': 'bolt://primary:7687',
                'grails.neo4j.username': 'neo',
                'grails.neo4j.password': 'secret',
                'grails.neo4j.connections.secondary.url': 'bolt://secondary:7687',
                'grails.neo4j.connections.secondary.type': 'embedded'
        ]))

        expect:
        Neo4jConnectionSourceFactory.isEmbeddedAvailable()
        factory.connectionSourcesConfigurationKey == 'grails.neo4j.connections'

        when:
        ConnectionSource<Driver, Neo4jConnectionSourceSettings> primary = factory.create(ConnectionSource.DEFAULT, environment)
        ConnectionSource<Driver, Neo4jConnectionSourceSettings> secondary = factory.create('secondary', environment)

        then:
        primary instanceof DefaultConnectionSource
        primary.name == ConnectionSource.DEFAULT
        primary.settings.url == 'bolt://primary:7687'
        primary.settings.username == 'neo'
        primary.settings.password == 'secret'
        primary.settings.type == Neo4jConnectionSourceSettings.ConnectionType.remote
        primary.source instanceof Driver
        secondary.settings.url == 'bolt://secondary:7687'
        secondary.settings.type == Neo4jConnectionSourceSettings.ConnectionType.embedded
        secondary.source instanceof Driver

        cleanup:
        [primary, secondary].each { it?.close() }
    }

}

@Entity
class NltOwner {

    Long id
    String name

}

@Entity
class NltOther {

    Long id
    String name

}
