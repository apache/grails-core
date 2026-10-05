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
import org.hibernate.boot.Metadata
import org.hibernate.mapping.PersistentClass

import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity

/**
 * Describes how {@link GrailsDomainGenerator} states the indexes a mapping asks for with {@code index:} on a column. The
 * names and the column order are the domain binder's ({@code IndexBinder}: {@code <table>_<column>_idx} for
 * {@code true}, the given names for a string, several names separated by commas, and {@code false} is none); the
 * differential spec compares them on every domain class.
 */
class GrailsDomainGeneratorIndexSpec extends GrailsDomainGeneratorSupport {

    void setupSpec() {
        manager.registerDomainClasses(
                GenIdxTarget, GenIdxOwner, GenIdxChild, GenIdxJoinedRoot, GenIdxJoinedChild, GenIdxTypeless, GenIdxCollections)
    }

    void "a column index is stated on the table with the name the binder gives it"() {
        when:
        Map<GrailsHibernatePersistentEntity, Class<?>> classes = generateGroup(
                GenIdxTarget, GenIdxOwner, GenIdxChild, GenIdxJoinedRoot, GenIdxJoinedChild)
        Map<String, List<String>> indexes = generatedIndexes(classes[entity(GenIdxOwner)])

        then:
        indexes['gen_idx_owner_plain_idx'] == ['plain']
        indexes['gen_idx_named'] == ['named']
        indexes['gen_idx_target'] == ['target_id']
        indexes['gen_idx_status'] == ['status']
        indexes['gen_idx_zip'] == ['home_zip']
    }

    void "several names on one column and one name on several columns make composite and overlapping indexes"() {
        when:
        Map<GrailsHibernatePersistentEntity, Class<?>> classes = generateGroup(
                GenIdxTarget, GenIdxOwner, GenIdxChild, GenIdxJoinedRoot, GenIdxJoinedChild)
        Map<String, List<String>> indexes = generatedIndexes(classes[entity(GenIdxOwner)])

        then:
        indexes['gen_idx_shared'] == ['shared1', 'shared2']
        indexes['gen_idx_other'] == ['shared2']
    }

    void "false is not an index"() {
        when:
        Map<GrailsHibernatePersistentEntity, Class<?>> classes = generateGroup(
                GenIdxTarget, GenIdxOwner, GenIdxChild, GenIdxJoinedRoot, GenIdxJoinedChild)

        then:
        !generatedIndexes(classes[entity(GenIdxOwner)]).values().any { it.contains('off') }
        generatedIndexes(newGenerator().generate(entity(GenIdxTypeless), getClass().classLoader)).isEmpty()
    }

    void "an index on a single-table subclass is stated on the table of the hierarchy, which is the root's"() {
        when:
        Map<GrailsHibernatePersistentEntity, Class<?>> classes = generateGroup(
                GenIdxTarget, GenIdxOwner, GenIdxChild, GenIdxJoinedRoot, GenIdxJoinedChild)

        then:
        generatedIndexes(classes[entity(GenIdxOwner)])['gen_idx_owner_child_col_idx'] == ['child_col']
        !classes[entity(GenIdxChild)].isAnnotationPresent(Table)
    }

    void "an index on a joined subclass is stated on the subclass's own table"() {
        when:
        Map<GrailsHibernatePersistentEntity, Class<?>> classes = generateGroup(
                GenIdxTarget, GenIdxOwner, GenIdxChild, GenIdxJoinedRoot, GenIdxJoinedChild)

        then:
        generatedIndexes(classes[entity(GenIdxJoinedChild)]) == ['gen_idx_joined_child_extra_idx': ['extra']]
        generatedIndexes(classes[entity(GenIdxJoinedRoot)]).isEmpty()
    }

    void "the generated indexes are the ones the binder bound, with the same names and column order"() {
        when:
        Map<GrailsHibernatePersistentEntity, Class<?>> classes = generateGroup(
                GenIdxTarget, GenIdxOwner, GenIdxChild, GenIdxJoinedRoot, GenIdxJoinedChild)

        then:
        [GenIdxOwner, GenIdxJoinedChild].every { Class<?> domain ->
            generatedIndexes(classes[entity(domain)]) == boundIndexes(domain)
        }
    }

    void "Hibernate's annotation binder reads the generated indexes as the binder bound them"() {
        given:
        Map<GrailsHibernatePersistentEntity, Class<?>> classes = generateGroup(
                GenIdxTarget, GenIdxOwner, GenIdxChild, GenIdxJoinedRoot, GenIdxJoinedChild)
        Metadata metadata = annotationMetadata(classes.values())

        expect:
        readIndexes(metadata.getEntityBinding(classes[entity(GenIdxOwner)].name)) == boundIndexes(GenIdxOwner)
        readIndexes(metadata.getEntityBinding(classes[entity(GenIdxJoinedChild)].name)) == boundIndexes(GenIdxJoinedChild)
    }

    void "an index on a collection property is rejected by name, because the binder indexes the key column of the collection table"() {
        when:
        generateGroup(GenIdxCollections)

        then:
        UnsupportedOperationException e = thrown()
        e.message.contains('GenIdxCollections')
        e.message.contains(property)
        e.message.contains('index or a unique group')
        entity(GenIdxCollections).persistentClass.getProperty(property).value.collectionTable.indexes.values()*.name == [indexName]

        where:
        property | indexName
        'tags'   | 'gen_idx_tags'
    }

    private Map<String, List<String>> boundIndexes(Class<?> domain) {
        return entity(domain).persistentClass.table.indexes.values().collectEntries { org.hibernate.mapping.Index index ->
            [(index.name): index.columns*.name]
        } as Map<String, List<String>>
    }

    private static Map<String, List<String>> readIndexes(PersistentClass persistentClass) {
        return persistentClass.table.indexes.values().collectEntries { org.hibernate.mapping.Index index ->
            [(index.name): index.columns*.name]
        } as Map<String, List<String>>
    }

    private static Map<String, List<String>> generatedIndexes(Class<?> generated) {
        Table table = generated.getAnnotation(Table)
        if (table == null) {
            return [:]
        }
        return table.indexes().collectEntries { jakarta.persistence.Index index ->
            [(index.name()): index.columnList().split(',').collect { it.trim() }]
        } as Map<String, List<String>>
    }
}

@Entity
class GenIdxTarget {

    String label
}

@Entity
class GenIdxOwner {

    String plain
    String named
    String shared1
    String shared2
    String off
    GenIdxTarget target
    GenIdxStatus status
    GenIdxAddress home

    static embedded = ['home']

    static mapping = {
        plain index: true
        named index: 'gen_idx_named'
        shared1 index: 'gen_idx_shared'
        shared2 index: 'gen_idx_shared, gen_idx_other'
        off index: false
        target index: 'gen_idx_target'
        status index: 'gen_idx_status'
    }
}

@Entity
class GenIdxChild extends GenIdxOwner {

    String childCol

    static mapping = {
        childCol index: true
    }
}

@Entity
class GenIdxJoinedRoot {

    String name

    static mapping = {
        tablePerHierarchy false
    }
}

@Entity
class GenIdxJoinedChild extends GenIdxJoinedRoot {

    String extra

    static mapping = {
        extra index: true
    }
}

@Entity
class GenIdxTypeless {

    String name
}

class GenIdxAddress {

    String zip

    static mapping = {
        zip index: 'gen_idx_zip'
    }
}

enum GenIdxStatus {
    OPEN, CLOSED
}

@Entity
class GenIdxCollections {

    Set<String> tags

    static hasMany = [tags: String]

    static mapping = {
        tags index: 'gen_idx_tags'
    }
}
