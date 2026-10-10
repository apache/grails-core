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

import spock.lang.Specification

import grails.gorm.annotation.Entity
import grails.neo4j.Relationship
import org.grails.datastore.gorm.neo4j.connections.Neo4jConnectionSourceSettings
import org.grails.datastore.gorm.neo4j.identity.SnowflakeIdGenerator
import org.grails.datastore.gorm.neo4j.proxy.HashcodeEqualsAwareProxyFactory
import org.grails.datastore.gorm.neo4j.proxy.Neo4jProxyFactory
import org.grails.datastore.mapping.model.config.GormMappingConfigurationStrategy

class Neo4jMappingContextConversionSpec extends Specification {

    void "values are converted to their native neo4j representation"() {
        given:
        Neo4jMappingContext context = new Neo4jMappingContext(new Neo4jConnectionSourceSettings())

        expect:
        context.convertToNative(null) == null
        context.convertToNative('text') == 'text'
        context.convertToNative(new StringBuilder('sb')) == 'sb'
        context.convertToNative(7L) == 7L
        context.convertToNative(7) == 7
        context.convertToNative(true) == true
        context.convertToNative([1, 2]) == [1, 2]
        context.convertToNative(1.5G) == 1.5d
        context.convertToNative(1.5G) instanceof Double
        context.convertToNative([1, 2] as byte[]) == [1, 2] as int[]
        context.convertToNative([1, 2] as byte[]) instanceof int[]
        context.convertToNative(NmcColour.RED) == 'RED'
        context.convertToNative(new Date(1000L)) == 1000L
        context.convertToNative(new URL('http://grails.org')) == 'http://grails.org'
        context.convertToNative(new NmcOpaque()) == 'opaque'
        Neo4jMappingContext.BASIC_TYPES.containsAll([String, Long, long, int[], String[], boolean])
    }

    void "entities are registered by their labels and use the neo4j proxy factory"() {
        given:
        Neo4jMappingContext context = new Neo4jMappingContext(new Neo4jConnectionSourceSettings(), NmcPerson, NmcFriendship)

        expect:
        context.findPersistentEntityForLabels(['NmcPerson']).javaClass == NmcPerson
        context.findPersistentEntityForLabels(['Nope']) == null
        context.getPersistentEntity(NmcFriendship.name) instanceof RelationshipPersistentEntity
        context.getPersistentEntity(NmcPerson.name) instanceof GraphPersistentEntity
        !(context.getPersistentEntity(NmcPerson.name) instanceof RelationshipPersistentEntity)
        context.proxyFactory instanceof Neo4jProxyFactory
        context.proxyFactory.is(context.proxyFactory)
        context.mappingSyntaxStrategy instanceof GormMappingConfigurationStrategy
        context.mappingFactory instanceof GraphGormMappingFactory
        context.idGenerator == null
        context.snowflakeIdGenerator instanceof SnowflakeIdGenerator
        context.snowflakeIdGenerator.is(context.snowflakeIdGenerator)
    }

    void "the deprecated constructors accept a default mapping closure"() {
        given:
        Closure mapping = { }

        when:
        Neo4jMappingContext plain = new Neo4jMappingContext()
        Neo4jMappingContext withMapping = new Neo4jMappingContext(mapping)
        Neo4jMappingContext withClasses = new Neo4jMappingContext(mapping, NmcPerson)

        then:
        plain.persistentEntities.empty
        withMapping.proxyFactory instanceof Neo4jProxyFactory
        !(withMapping.proxyFactory instanceof HashcodeEqualsAwareProxyFactory)
        withClasses.getPersistentEntity(NmcPerson.name) != null
    }

}

enum NmcColour {
    RED, GREEN
}

class NmcOpaque {

    @Override
    String toString() { 'opaque' }

}

@Entity
class NmcPerson {

    Long id
    String name

}

@Entity
class NmcFriendship implements Relationship<NmcPerson, NmcPerson> {

    Long id
    String since

}
