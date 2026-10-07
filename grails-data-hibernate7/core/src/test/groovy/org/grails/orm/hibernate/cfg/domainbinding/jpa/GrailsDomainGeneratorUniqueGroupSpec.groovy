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
package org.grails.orm.hibernate.cfg.domainbinding.jpa

import grails.gorm.annotation.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.boot.Metadata
import org.hibernate.mapping.PersistentClass

import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity

/**
 * Describes how {@link GrailsDomainGenerator} states the unique constraints a mapping asks for with {@code unique: 'group'}
 * or {@code unique: ['a', 'b']} on a column: one multi-column unique key per property that names a group, with the
 * binder's column order (the property's own column last) and the binder's name ({@code UK} and a hash of the table and
 * the columns). The differential spec compares them on every domain class.
 */
class GrailsDomainGeneratorUniqueGroupSpec extends GrailsDomainGeneratorSupport {

    void setupSpec() {
        manager.registerDomainClasses(GenUqTarget, GenUqOwner, GenUqChild, GenUqJoinedRoot, GenUqJoinedChild, GenUqEnum, GenUqCollection, GenUqPair)
    }

    void "a unique group is a unique constraint on the table over the columns of the group"() {
        when:
        Map<GrailsHibernatePersistentEntity, Class<?>> classes = generateAll()
        List<List<String>> constraints = generatedConstraints(classes[entity(GenUqOwner)]).values().toList()

        then: "the column of the property that names the group comes last, as the binder orders it"
        constraints.contains(['b', 'a'])
        constraints.contains(['d', 'c'])
        constraints.contains(['other_id', 'target_id'])
        constraints.contains(['home_city', 'home_street'])
    }

    void "a plain unique is not a group: it stays a unique column"() {
        when:
        Map<GrailsHibernatePersistentEntity, Class<?>> classes = generateAll()

        then:
        !generatedConstraints(classes[entity(GenUqOwner)]).values().any { it == ['solo'] }
        classes[entity(GenUqOwner)].getDeclaredField('solo').getAnnotation(jakarta.persistence.Column).unique()
    }

    void "a group of a single-table subclass is stated on the table of the hierarchy and one of a joined subclass on its own table"() {
        when:
        Map<GrailsHibernatePersistentEntity, Class<?>> classes = generateAll()

        then:
        generatedConstraints(classes[entity(GenUqOwner)]).values().toList().contains(['child_two', 'child_one'])
        !classes[entity(GenUqChild)].isAnnotationPresent(Table)
        generatedConstraints(classes[entity(GenUqJoinedChild)]).values().toList() == [['extra_two', 'extra_one']]
        generatedConstraints(classes[entity(GenUqJoinedRoot)]).isEmpty()
    }

    void "the generated unique constraints are the ones the binder bound, with the same names and column order"() {
        when:
        Map<GrailsHibernatePersistentEntity, Class<?>> classes = generateAll()

        then:
        [GenUqOwner, GenUqJoinedChild].every { Class<?> domain ->
            generatedConstraints(classes[entity(domain)]) == boundConstraints(domain)
        }
    }

    void "Hibernate's annotation binder reads the generated unique constraints as the binder bound them"() {
        given:
        Map<GrailsHibernatePersistentEntity, Class<?>> classes = generateAll()
        Metadata metadata = annotationMetadata(classes.values())

        expect:
        readConstraints(metadata.getEntityBinding(classes[entity(GenUqOwner)].name)) == boundConstraints(GenUqOwner)
        readConstraints(metadata.getEntityBinding(classes[entity(GenUqJoinedChild)].name)) == boundConstraints(GenUqJoinedChild)
    }

    void "a unique group on an enum property is stated, although the binder never creates the key"() {
        when:
        Map<GrailsHibernatePersistentEntity, Class<?>> classes = generateGroup(GenUqEnum)
        ConstraintFacets facets = newGenerator().constraintFacets(entity(GenUqEnum))

        then: "EnumTypeBinder never calls CreateKeyForProps, which the mapping does not mean: the generator states the key and says the binder does not"
        generatedConstraints(classes[entity(GenUqEnum)]).values().toList() == [['other', 'state']]
        facets.uniqueKeys()*.bound() == [false]
        entity(GenUqEnum).persistentClass.table.uniqueKeys.values().every { it.columns.size() < 2 }
    }

    void "a unique group over the columns of a composite identifier states the order of its primary key, as the binder gives it"() {
        when:
        generateGroup(GenUqPair)

        then: "the group names world first, and the key itself is dropped by Hibernate, as the primary key's ordering"
        newGenerator().constraintFacets(entity(GenUqPair)).primaryKeyOrder() == ['world', 'hello']
        newGenerator().constraintFacets(entity(GenUqPair)).uniqueKeys().isEmpty()
        entity(GenUqPair).persistentClass.table.primaryKey.orderingUniqueKey.columns*.name == ['world', 'hello']
    }

    void "an entity without such a group states no order for its primary key"() {
        expect:
        newGenerator().constraintFacets(entity(GenUqTarget)).primaryKeyOrder() == null
    }

    void "a unique group on a collection property is rejected by name, because the binder makes it a key of the collection table"() {
        when:
        generateGroup(GenUqCollection)

        then: "the key names a column of the owner's table, which the collection table does not have"
        UnsupportedOperationException e = thrown()
        e.message.contains('GenUqCollection')
        e.message.contains('tags')
        e.message.contains('index or a unique group')
        entity(GenUqCollection).persistentClass.getProperty('tags').value.collectionTable.uniqueKeys.values().any {
            it.columns*.name.contains('x')
        }
    }

    private Map<GrailsHibernatePersistentEntity, Class<?>> generateAll() {
        return generateGroup(GenUqTarget, GenUqOwner, GenUqChild, GenUqJoinedRoot, GenUqJoinedChild)
    }

    private Map<String, List<String>> boundConstraints(Class<?> domain) {
        return readConstraints(entity(domain).persistentClass)
    }

    private static Map<String, List<String>> readConstraints(PersistentClass persistentClass) {
        // Hibernate derives a single-column unique key, named UK_ and a hash, from every unique column once the metadata is complete
        return persistentClass.table.uniqueKeys.values().findAll { org.hibernate.mapping.UniqueKey key ->
            key.columns.size() > 1
        }.collectEntries { org.hibernate.mapping.UniqueKey key ->
            [(key.name): key.columns*.name]
        } as Map<String, List<String>>
    }

    private static Map<String, List<String>> generatedConstraints(Class<?> generated) {
        Table table = generated.getAnnotation(Table)
        if (table == null) {
            return [:]
        }
        return table.uniqueConstraints().collectEntries { UniqueConstraint constraint ->
            [(constraint.name()): constraint.columnNames().toList()]
        } as Map<String, List<String>>
    }
}

@Entity
class GenUqTarget {

    String label
}

@Entity
class GenUqOwner {

    String a
    String b
    String c
    String d
    String solo
    GenUqTarget target
    GenUqTarget other
    GenUqAddress home

    static embedded = ['home']

    static constraints = {
        a unique: ['b']
        solo unique: true
    }

    static mapping = {
        c unique: 'd'
        target unique: 'other'
    }
}

@Entity
class GenUqChild extends GenUqOwner {

    String childOne
    String childTwo

    static mapping = {
        childOne unique: 'childTwo'
    }
}

@Entity
class GenUqJoinedRoot {

    String name

    static mapping = {
        tablePerHierarchy false
    }
}

@Entity
class GenUqJoinedChild extends GenUqJoinedRoot {

    String extraOne
    String extraTwo

    static mapping = {
        extraOne unique: 'extraTwo'
    }
}

class GenUqAddress {

    String street
    String city

    static mapping = {
        street unique: 'city'
    }
}

@Entity
class GenUqEnum {

    GenUqState state
    String other

    static mapping = {
        state unique: 'other'
    }
}

enum GenUqState {
    ON, OFF
}

@Entity
class GenUqCollection {

    String x
    Set<String> tags

    static hasMany = [tags: String]

    static mapping = {
        tags unique: 'x'
    }
}

@Entity
class GenUqPair implements Serializable {
    Long hello
    Long world
    static constraints = {
        hello unique: 'world'
    }
    static mapping = {
        version false
        id composite: ['hello', 'world']
    }
}
