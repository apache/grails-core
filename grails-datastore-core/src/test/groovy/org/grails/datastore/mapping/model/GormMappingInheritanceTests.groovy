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

import org.junit.jupiter.api.Disabled

import grails.gorm.annotation.Entity
import org.grails.datastore.mapping.model.types.*
import org.junit.jupiter.api.Test
import spock.lang.Ignore

import static org.junit.jupiter.api.Assertions.*

/**
 * Tests for correct mapping of entities with inheritance.
 */
class GormMappingInheritanceTests {

    @Test
    void testInheritedMultiLevelMapping() {
        def context = new TestMappingContext()
        context.addPersistentEntities(DerivedEntityChildA, DerivedEntity, DerivedEntityChildB, DerivedEntityChildC)
        assertEquals 2, context.persistentEntitiesByDiscriminator.size()
        def secondEntityMappingKey = context.persistentEntitiesByDiscriminator.keySet().find { it.decapitalizedName == 'secondEntity' }
        assertNotNull secondEntityMappingKey
        def secondEntityMappings = context.persistentEntitiesByDiscriminator.get(secondEntityMappingKey)
        assertEquals 4, secondEntityMappings.size()
    }

    @Test
    void testInheritedMappedBy() {
        def context = new TestMappingContext()
        context.addPersistentEntity(SpecialUser)
        assert 2 == context.persistentEntities.size()

        def user = context.getPersistentEntity(SpecialUser.name)

        Association foesAssociation = user.getPropertyByName("foes")
        assert (foesAssociation instanceof OneToMany)
        assert !foesAssociation.isBidirectional()

        Association friendsAssociation = user.getPropertyByName("friends")
        assert (friendsAssociation instanceof OneToMany)
        assert !friendsAssociation.isBidirectional()

        Association bestBuddyAssociation = user.getPropertyByName("bestBuddy")
        assert (bestBuddyAssociation instanceof OneToOne)
        assert !bestBuddyAssociation.isBidirectional()

        Association specialFriendsAssociation = user.getPropertyByName("specialFriends")
        assert (specialFriendsAssociation instanceof OneToMany)
        assert !specialFriendsAssociation.isBidirectional()

    }

    @Test
    void testInheritedTransients() {
        def context = new TestMappingContext()
        context.addPersistentEntity(DerivedEntity)
        assertEquals 2, context.persistentEntities.size()

        def derived = context.getPersistentEntity(DerivedEntity.name)
        assertNull derived.getPropertyByName('baz')
        assertNull derived.getPropertyByName('bar')
    }

    @Test
    void testInheritedEmbeddeds() {
        def context = new TestMappingContext()
        context.addPersistentEntity(DerivedEmbeddedTest)

        def derived = context.getPersistentEntity(DerivedEmbeddedTest.name)
        def property = derived.getPropertyByName('testEntity')
        assertTrue property instanceof Embedded
        property = derived.getPropertyByName('testEntity2')
        assertTrue property instanceof Embedded
    }

    @Test
    void testInheritedAssociations() {
        def context = new TestMappingContext()
        context.addPersistentEntity(DerivedChild)
        assertEquals 3, context.persistentEntities.size()

        def derivedChild = context.getPersistentEntity(DerivedChild.name)
        Association parentAssociation = derivedChild.getPropertyByName("parent")
        assertTrue parentAssociation instanceof ManyToOne
        assertTrue parentAssociation.bidirectional
        assertEquals parentAssociation.associatedEntity, context.getPersistentEntity(Parent.name)
        assertTrue parentAssociation.inverseSide.owningSide
    }

    @Test
    void testInheritedMapping() {
        def context = new TestMappingContext()
        context.addPersistentEntity(MappingTest2)
        assertEquals 2, context.persistentEntities.size()

        def test = context.getPersistentEntity(MappingTest2.name)
        PersistentProperty property = test.getPropertyByName("toIndex1")
        assertTrue property.mapping.mappedForm.index
        property = test.getPropertyByName("toIndex2")
        assertTrue property.mapping.mappedForm.index
        property = test.getPropertyByName("doNotIndex")
        assertFalse property.mapping.mappedForm.index
    }
}

@Entity
class DerivedEntity extends SecondEntity {
    String baz

    static transients = ['baz']
}

@Entity
class SpecialUser extends User {
    Set specialFriends

    static hasMany = [specialFriends: User]

    // prevent bidirectional associations here
    static mappedBy = [specialFriends: null]
}

@Entity
class Parent {
    Long id
    Set children

    static hasMany = [children: BaseChild]
}

@Entity
class BaseChild {
    Long id

    Parent parent

    static belongsTo = [parent: Parent]
}

@Entity
class DerivedChild extends BaseChild {
    String prop
}

@Entity
class EmbeddedTest {
    Long id

    TestEntity testEntity

    static embedded = ['testEntity']
}

@Entity
class DerivedEmbeddedTest extends EmbeddedTest {
    TestEntity testEntity2
    static embedded = ['testEntity2']
}

@Entity
class MappingTest {
    Long id

    String toIndex1

    static mapping = {
        toIndex1 index: true
    }
}

@Entity
class MappingTest2 extends MappingTest {

    String toIndex2
    String doNotIndex

    static mapping = {
        toIndex2 index: true
        doNotIndex index: false
    }
}

@Entity
class DerivedEntityChildA extends DerivedEntity {
}

@Entity
class DerivedEntityChildB extends DerivedEntity {
}

@Entity
class DerivedEntityChildC extends DerivedEntity {
}