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
package org.grails.datastore.mapping.model

import grails.gorm.annotation.Entity
import org.grails.datastore.mapping.model.config.JpaMappingConfigurationStrategy

import static org.junit.jupiter.api.Assertions.*


import org.junit.jupiter.api.Test
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.model.types.ManyToOne
import org.grails.datastore.mapping.model.types.OneToMany
import org.grails.datastore.mapping.model.types.OneToOne

/**
 * @author Graeme Rocher
 * @since 1.1
 */
class GormMappingSyntaxTests {

    @Test
    void testIsEntity() {

        def strategy = new JpaMappingConfigurationStrategy(new TestMappedPropertyFactory())

        assert strategy.isPersistentEntity(TestEntity)
        assert strategy.isPersistentEntity(JavaEntity)
        assert !strategy.isPersistentEntity(GormMappingSyntaxTests)
    }

    @Test
    void testGetIdentity() {
        def context = new TestMappingContext()
        context.addPersistentEntity(TestEntity)
        def strategy = context.mappingSyntaxStrategy
        def id = strategy.getIdentity(TestEntity, context)

        assert id != null

        assert id.type == Long
        assert id.name == 'id'
    }

    @Test
    void testGetSimplePersistentProperties() {
        def context = new TestMappingContext()
        context.addPersistentEntity(TestEntity)
        context.addPersistentEntity(SecondEntity)
        def strategy = context.mappingSyntaxStrategy
        def props = strategy.getPersistentProperties(TestEntity,context)
        assert props.size() == 3
    }

    @Test
    void testUnidirectionalOneToOneAssociation() {
        def context = new TestMappingContext()
        context.addPersistentEntity(TestEntity)

        assert 2 == context.persistentEntities.size()

        def testEntity = context.getPersistentEntity(TestEntity.name)

        def association = testEntity.getPropertyByName("second")

        assert association != null

        assert (association instanceof OneToOne)

        OneToOne toOne = association
        assert toOne.foreignKeyInChild
        assert toOne.associatedEntity != null

        assert toOne.associatedEntity == context.getPersistentEntity(SecondEntity.name)
        assert toOne.referencedPropertyName == null
        assert toOne.bidirectional == false
        assert toOne.owningSide == true
    }

    @Test
    void testUnidirectionalOneToMany() {
        def context = new TestMappingContext()
        context.addPersistentEntity(Publisher)

        assert 3 == context.persistentEntities.size()

        def publisher = context.getPersistentEntity(Publisher.name)

        assert publisher != null

        Association oneToMany = publisher.getPropertyByName("authors")
        assert oneToMany != null
        assert !oneToMany.bidirectional
        assert !oneToMany.owningSide
        assert (oneToMany instanceof OneToMany)
    }

    @Test
    void testManyToOneAssociation() {
        def context = new TestMappingContext()
        context.addPersistentEntity(Book)

        assert 2 == context.persistentEntities.size()

        def book = context.getPersistentEntity(Book.name)

        assert book != null
        Association authorAssociation = book.getPropertyByName("author")

        assert authorAssociation != null
        assert (authorAssociation instanceof ManyToOne)
        assert authorAssociation.bidirectional
        assert !authorAssociation.owningSide

        Association inverse = authorAssociation.inverseSide
        assert inverse != null

        assert "books" == inverse.name
        assert Author == inverse.owner.javaClass
        assert inverse.inverseSide != null
        assert inverse.bidirectional
        assert (inverse instanceof OneToMany)
        assert inverse.owningSide
    }

    @Test
    void testIndexedProperty() {
        def context = new TestMappingContext()
        context.addPersistentEntity(EntityWithIndexedProperty)

        assertEquals 1, context.persistentEntities.size()

        assertNotNull context.getPersistentEntity(EntityWithIndexedProperty.name)

        assertEquals 3, context.mappingSyntaxStrategy.getPersistentProperties(EntityWithIndexedProperty, context).size()
    }

    @Test
    void testForceUnidirectional() {
        def context = new TestMappingContext()
        context.addPersistentEntity(User)
        assert 1 == context.persistentEntities.size()

        def user = context.getPersistentEntity(User.name)

        Association foesAssociation = user.getPropertyByName("foes")
        assert (foesAssociation instanceof OneToMany)
        assert !foesAssociation.isBidirectional()

        Association friendsAssociation = user.getPropertyByName("friends")
        assert (friendsAssociation instanceof OneToMany)
        assert !friendsAssociation.isBidirectional()

        Association bestBuddyAssociation = user.getPropertyByName("bestBuddy")
        assert (bestBuddyAssociation instanceof OneToOne)
        assert !bestBuddyAssociation.isBidirectional()

    }

    @jakarta.persistence.Entity
    class JavaEntity {}
}

@Entity
class Book {
    Long id
    String title
    Author author
    static belongsTo = [author:Author]
}

@Entity
class Author {
    Long id
    String name
    Set books
    def shouldBeIgnored
    static hasMany = [books:Book]
}

@Entity
class Publisher {
    Long id
    Set authors
    static hasMany = [authors:Author]
}

@Entity
class TestEntity {
    Long id
    Long version
    String name
    String bar

    SecondEntity second

    static hasOne = [second:SecondEntity]
    static transients = ['bar']
}

@Entity
class SecondEntity {
    Long id
    String name
    String bar

    static transients = ['bar']
}

@Entity
class EntityWithIndexedProperty {
    Long id
    Long version
    String name
    String bar

    String getSectionContent(int section) {}
    void setSectionContent(int section, String content) {}
}

@Entity
class User {
    Long id
    Long version
    String name
    User bestBuddy
    Set foes
    Set friends
    static hasMany = [ foes: User, friends: User]

    // prevent bidirectional associations here
    static mappedBy = [ bestBuddy:null, foes:null, friends:null]
}

