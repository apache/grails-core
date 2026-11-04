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

package org.grails.gorm.graphql.entity

import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.model.types.ToMany
import org.grails.gorm.graphql.HibernateSpec
import org.grails.gorm.graphql.domain.general.toone.BelongsToHasOne
import org.grails.gorm.graphql.domain.general.toone.CircularOne
import org.grails.gorm.graphql.domain.general.toone.HasOne
import org.grails.gorm.graphql.domain.general.toone.One
import org.grails.gorm.graphql.domain.general.toone.ToOne

class EntityFetchOptionsSpec extends HibernateSpec {

    List<Class> getDomainClasses() { [One, ToOne, CircularOne, HasOne, BelongsToHasOne] }

    void "test constructing with a null entity throws"() {
        when:
        new EntityFetchOptions((org.grails.datastore.mapping.model.PersistentEntity) null)

        then:
        IllegalArgumentException ex = thrown(IllegalArgumentException)
        ex.message == 'Cannot retrieve fetch options for a null entity. Is GORM initialized?'
    }

    void "test constructing from a class delegates to the persistent entity constructor"() {
        when:
        EntityFetchOptions options = new EntityFetchOptions(ToOne)

        then:
        options.getAssociations().keySet().containsAll(['one', 'circularOne'])
    }

    void "test getAssociations returns the entity's associations keyed by property name"() {
        given:
        EntityFetchOptions options = new EntityFetchOptions(mappingContext.getPersistentEntity(ToOne.name))

        expect:
        options.getAssociations().keySet().containsAll(['one', 'circularOne'])
        !options.getAssociations().containsKey('string')
    }

    void "test getFetchArgument with no properties returns an empty map"() {
        given:
        EntityFetchOptions options = new EntityFetchOptions(mappingContext.getPersistentEntity(ToOne.name))

        expect:
        options.getFetchArgument([] as Set<String>) == [:]
    }

    void "test getFetchArgument with properties builds a fetch join map"() {
        given:
        EntityFetchOptions options = new EntityFetchOptions(mappingContext.getPersistentEntity(ToOne.name))

        expect:
        options.getFetchArgument(['one', 'circularOne'] as Set<String>) == [
                fetch: [one: 'join', circularOne: 'join']
        ]
    }

    void "test isForeignKeyInChild is true for a ToMany association"() {
        given:
        EntityFetchOptions options = new EntityFetchOptions(mappingContext.getPersistentEntity(HasOne.name))
        Association toMany = Stub(ToMany)

        expect:
        options.isForeignKeyInChild(toMany)
    }

    void "test isForeignKeyInChild is true for a hasOne association where the child owns the foreign key"() {
        given:
        EntityFetchOptions options = new EntityFetchOptions(mappingContext.getPersistentEntity(HasOne.name))
        Association association = options.getAssociations().get('one')

        expect:
        options.isForeignKeyInChild(association)
    }

    void "test isForeignKeyInChild is false for a plain toOne association"() {
        given:
        EntityFetchOptions options = new EntityFetchOptions(mappingContext.getPersistentEntity(ToOne.name))
        Association association = options.getAssociations().get('one')

        expect:
        !options.isForeignKeyInChild(association)
    }
}
