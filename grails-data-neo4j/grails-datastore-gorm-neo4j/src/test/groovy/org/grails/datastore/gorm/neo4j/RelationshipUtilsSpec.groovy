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

import grails.gorm.annotation.Entity
import grails.neo4j.Direction
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.Association
import spock.lang.Specification

import static org.grails.datastore.gorm.neo4j.RelationshipUtils.*
/**
 * Created by graemerocher on 24/11/16.
 */
class RelationshipUtilsSpec extends Specification {

    void "Test get relationship type for association"() {
        given:
        def context = new Neo4jMappingContext()

        context.addPersistentEntities(Foo, Bar)
        context.initialize()
        PersistentEntity entity = context.getPersistentEntity(entty.name)
        Association property = entity.getPropertyByName(association)

        expect:
        matchForAssociation(property) == match
        property.isBidirectional() == bidirectional
        property.isOwningSide() == owningSide

        where:
        entty |    association             |  match                         |   bidirectional   |   owningSide
        Foo   |   'bidirectionalBars'      | '-[:BIDIRECTIONALBARS]->'      |   true            |   true
        Bar   |   'foo'                    | '<-[:BIDIRECTIONALBARS]-'      |   true            |   false
        Foo   |   'evenMoreBars'           | '<-[:EVEN_MOREEE]-'            |   false           |   true
        Foo   |   'soManyBars'             | '<-[:SOMANYBARS]->'            |   false           |   true
        Foo   |   'bars'                   | '-[:BARS]->'                   |   false           |   true
        Foo   |   'bar'                    | '-[:BAR]->'                    |   false           |   true
        Foo   |   'anotherBar'             | '-[:ANOTHERBAR]->'             |   false           |   true
    }


}
@Entity
class Foo {

    static hasMany = [bars:Bar, moreBars:Bar, evenMoreBars:Bar, soManyBars:Bar, bidirectionalBars:Bar]
    static hasOne = [bar:Bar]
    Bar anotherBar

    static mappedBy = [
        bars: 'none',
        moreBars: 'none',
        evenMoreBars: 'none',
        soManyBars: 'none',
        anotherBar: 'none',
        bar: 'none',
        bidirectionalBars:'foo'
    ]
    static mapping = {
        moreBars type:"MORE_BARZ"
        evenMoreBars type:"EVEN_MOREEE", direction:Direction.INCOMING
        soManyBars direction:Direction.BOTH
    }
}
@Entity
class Bar {
    static belongsTo = [foo:Foo]
    static mappedBy = [foo:'bidirectionalBars']
}
