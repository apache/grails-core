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
import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.JoinColumn
import jakarta.persistence.JoinColumns
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.Version
import org.hibernate.boot.Metadata
import org.hibernate.mapping.Component
import org.hibernate.mapping.PersistentClass
import org.hibernate.mapping.Property
import org.hibernate.mapping.ToOne

import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity

/**
 * Describes how {@link GrailsDomainGenerator} states a composite identifier ({@code id composite: [...]}) and the foreign keys
 * that point at one. The binder builds one identifier component from the parts, whose columns are the primary key, and a
 * foreign key to such an entity has one column for each identifier property. The generated class gets an {@code @EmbeddedId}
 * of a generated {@code @Embeddable} that holds the parts, and a foreign key states {@code @JoinColumns} that name the column of
 * the key each of them points at. The differential spec compares the identifier and the foreign keys on every domain class.
 */
class GrailsDomainGeneratorCompositeIdSpec extends GrailsDomainGeneratorSupport {

    void setupSpec() {
        manager.registerDomainClasses(
                GenCidSimple, GenCidTarget, GenCidParts, GenCidRef, GenCidIndexed, GenCidNested, GenCidParent, GenCidChild)
    }

    void "a composite identifier is an @EmbeddedId of a generated embeddable that holds the parts, and the entity keeps the other properties"() {
        when:
        Class<?> generated = generateGroup(GenCidSimple).values().first()
        Class<?> key = generated.getDeclaredField('id').type

        then:
        generated.getDeclaredField('id').isAnnotationPresent(EmbeddedId)
        key.isAnnotationPresent(Embeddable)
        Serializable.isAssignableFrom(key)
        key.name == generated.name + '_Id'
        key.declaredFields*.name.toSet() == ['last', 'age'].toSet()
        generated.declaredFields*.name.toSet() == ['id', 'version', 'note'].toSet()
        generated.getDeclaredField('version').isAnnotationPresent(Version)
    }

    void "the parts are never null, whatever the mapping says, and are named as the binder names them"() {
        when:
        Class<?> key = generateGroup(GenCidSimple).values().first().getDeclaredField('id').type

        then:
        key.getDeclaredField('last').getAnnotation(Column).name() == 'last'
        !key.getDeclaredField('last').getAnnotation(Column).nullable()
        key.getDeclaredField('age').getAnnotation(Column).name() == 'age'
        !key.getDeclaredField('age').getAnnotation(Column).nullable()
    }

    void "a many-to-one part is an association of the embeddable with its foreign key column"() {
        when:
        Map<GrailsHibernatePersistentEntity, Class<?>> classes = generateGroup(GenCidParts, GenCidTarget)
        Class<?> key = classes[entity(GenCidParts)].getDeclaredField('id').type

        then:
        key.getDeclaredField('owner').isAnnotationPresent(ManyToOne)
        !key.getDeclaredField('owner').getAnnotation(ManyToOne).optional()
        key.getDeclaredField('owner').getAnnotation(JoinColumn).name() == 'owner_id'
        !key.getDeclaredField('owner').getAnnotation(JoinColumn).nullable()
        key.getDeclaredField('owner').type == classes[entity(GenCidTarget)]
    }

    void "Hibernate's annotation binder reads the composite identifier as the binder bound it"() {
        given:
        Map<GrailsHibernatePersistentEntity, Class<?>> classes = generateGroup(GenCidSimple, GenCidParts, GenCidTarget)
        Metadata metadata = annotationMetadata(classes.values())
        PersistentClass bound = entity(domain).persistentClass
        PersistentClass read = metadata.getEntityBinding(classes[entity(domain)].name)

        expect:
        parts(read) == parts(bound)
        read.table.primaryKey.columns*.name.toSet() == bound.table.primaryKey.columns*.name.toSet()
        read.table.foreignKeys.values().collect { it.columns*.name.toSet() }.toSet() ==
                bound.table.foreignKeys.values().collect { it.columns*.name.toSet() }.toSet()

        where:
        domain << [GenCidSimple, GenCidParts]
    }

    void "a foreign key to a composite identifier has a join column for each identifier property, naming the key column it points at"() {
        when:
        Map<GrailsHibernatePersistentEntity, Class<?>> classes = generateGroup(GenCidRef, GenCidSimple)
        JoinColumns plain = classes[entity(GenCidRef)].getDeclaredField('plain').getAnnotation(JoinColumns)
        JoinColumns named = classes[entity(GenCidRef)].getDeclaredField('named').getAnnotation(JoinColumns)

        then: "the default names are the table of the target and the name of the identifier property, in the order of the mapping"
        plain.value()*.name() == ['gen_cid_simple_last', 'gen_cid_simple_age']
        plain.value()*.referencedColumnName() == ['last', 'age']
        plain.value().every { it.nullable() }
        named.value()*.name() == ['n_last', 'n_age']
        named.value()*.referencedColumnName() == ['last', 'age']
        !classes[entity(GenCidRef)].getDeclaredField('plain').isAnnotationPresent(JoinColumn)
    }

    void "Hibernate's annotation binder reads the foreign key to a composite identifier as the binder bound it"() {
        given:
        Map<GrailsHibernatePersistentEntity, Class<?>> classes = generateGroup(GenCidRef, GenCidSimple)
        Metadata metadata = annotationMetadata(classes.values())
        PersistentClass bound = entity(GenCidRef).persistentClass
        PersistentClass read = metadata.getEntityBinding(classes[entity(GenCidRef)].name)

        expect: "the same columns for each association, in whatever order Hibernate arranges them, and the same constraints"
        ['plain', 'named'].every { String name ->
            ((ToOne) bound.getProperty(name).value).selectables*.text.toSet() == ((ToOne) read.getProperty(name).value).selectables*.text.toSet() &&
                    bound.getProperty(name).columns*.nullable.toSet() == read.getProperty(name).columns*.nullable.toSet()
        }
        read.table.foreignKeys.values().collect { it.columns*.name.toSet() }.toSet() ==
                bound.table.foreignKeys.values().collect { it.columns*.name.toSet() }.toSet()
    }

    void "an index on a part of the identifier is an index of the table, as the binder binds it"() {
        when:
        Class<?> generated = generateGroup(GenCidIndexed).values().first()

        then:
        generated.getAnnotation(Table).indexes()*.name() == ['gen_cid_a_idx']
        generated.getAnnotation(Table).indexes()*.columnList() == ['a']
        entity(GenCidIndexed).persistentClass.table.indexes.keySet() == ['gen_cid_a_idx'].toSet()
    }

    void "an identifier part that refers to an entity with a composite identifier is rejected by name"() {
        when:
        generateGroup(GenCidNested, GenCidSimple)

        then:
        UnsupportedOperationException e = thrown()
        e.message.contains('GenCidNested')
        e.message.contains('parent')
        e.message.contains('composite identifier')
    }

    void "a composite identifier with subclasses is rejected by name"() {
        when:
        generateGroup(GenCidParent, GenCidChild)

        then:
        UnsupportedOperationException e = thrown()
        e.message.contains('GenCidParent')
        e.message.contains('subclasses')
    }

    private static Map<String, Map<String, Object>> parts(PersistentClass persistentClass) {
        Component id = (Component) persistentClass.identifier
        return id.properties.collectEntries { Property part ->
            [(part.name): [columns: part.columns*.name, nullable: part.columns*.nullable, toOne: part.value instanceof ToOne]]
        } as Map<String, Map<String, Object>>
    }
}

@Entity
class GenCidSimple implements Serializable {

    String last
    Long age
    String note

    static mapping = {
        id composite: ['last', 'age']
    }
}

@Entity
class GenCidTarget {

    String label
}

@Entity
class GenCidParts implements Serializable {

    GenCidTarget owner
    String name
    String extra

    static mapping = {
        id composite: ['name', 'owner']
    }
}

@Entity
class GenCidRef {

    GenCidSimple plain
    GenCidSimple named

    static mapping = {
        named {
            column name: 'n_last'
            column name: 'n_age'
        }
    }
}

@Entity
class GenCidIndexed implements Serializable {

    String a
    String b

    static mapping = {
        id composite: ['a', 'b']
        a index: 'gen_cid_a_idx'
    }
}

@Entity
class GenCidNested implements Serializable {

    GenCidSimple parent
    String name

    static mapping = {
        id composite: ['parent', 'name']
    }
}

@Entity
class GenCidParent implements Serializable {

    String a
    String b

    static mapping = {
        id composite: ['a', 'b']
    }
}

@Entity
class GenCidChild extends GenCidParent {

    String c
}
