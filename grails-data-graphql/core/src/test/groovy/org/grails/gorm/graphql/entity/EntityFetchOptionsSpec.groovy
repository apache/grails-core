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

import graphql.execution.MergedField
import graphql.language.Field
import graphql.language.FragmentSpread
import graphql.language.SelectionSet
import graphql.schema.DataFetchingEnvironment
import spock.lang.Specification

import grails.gorm.annotation.Entity
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.grails.datastore.mapping.model.PersistentEntity

class EntityFetchOptionsSpec extends Specification {

    KeyValueMappingContext context = new KeyValueMappingContext('test')

    void setup() {
        context.addPersistentEntities(EfoAuthor, EfoBook, EfoPublisher)
    }

    private static Field field(String name, Field... children) {
        Field.Builder builder = Field.newField(name)
        if (children) {
            builder.selectionSet(SelectionSet.newSelectionSet(children.toList()).build())
        }
        builder.build()
    }

    void 'a null entity is rejected'() {
        when:
        new EntityFetchOptions((PersistentEntity) null)

        then:
        IllegalArgumentException e = thrown()
        e.message == 'Cannot retrieve fetch options for a null entity. Is GORM initialized?'
    }

    void 'the associations of the entity are exposed by name'() {
        given:
        EntityFetchOptions options = new EntityFetchOptions(context.getPersistentEntity(EfoBook.name))

        expect:
        options.associations.keySet() == ['author', 'publisher'] as Set
        options.associations.author.associatedEntity.javaClass == EfoAuthor
    }

    void 'only associations whose selections need more than the identifier are joined'() {
        given:
        EntityFetchOptions options = new EntityFetchOptions(context.getPersistentEntity(EfoBook.name))

        expect:
        options.getJoinProperties([field('title')]) == [] as Set
        options.getJoinProperties((List<Field>) null) == [] as Set
        options.getJoinProperties([field('author', field('id'))]) == [] as Set
        options.getJoinProperties([field('author', field('name'))]) == ['author'] as Set
        options.getJoinProperties([field('author', field('id'), field('name'))]) == ['author'] as Set
        options.getJoinProperties([field('author')]) == ['author'] as Set
        options.getJoinProperties([field('publisher', field('id'))]) == [] as Set
        options.getJoinProperties([field('publisher', field('name'))]) == ['publisher'] as Set
        options.getJoinProperties([field('author', field('books', field('title')))]) == ['author'] as Set
    }

    void 'collections are always joined unless they are skipped'() {
        given:
        EntityFetchOptions options = new EntityFetchOptions(context.getPersistentEntity(EfoAuthor.name))

        expect:
        options.getJoinProperties([field('books', field('id'))]) == ['books'] as Set
        options.getJoinProperties([field('books', field('id'))], true) == [] as Set
        options.getJoinProperties([field('books', field('publisher', field('name')))]) == ['books', 'books.publisher'] as Set
        options.getJoinProperties([field('books', field('author', field('name')))]) == ['books'] as Set
    }

    void 'a projection name prefixes the joined properties'() {
        given:
        EntityFetchOptions options = new EntityFetchOptions(context.getPersistentEntity(EfoBook.name), 'book')

        expect:
        options.getJoinProperties([field('author', field('name'))]) == ['book.author'] as Set
    }

    void 'the fetch argument maps every joined property to join'() {
        given:
        EntityFetchOptions options = new EntityFetchOptions(context.getPersistentEntity(EfoBook.name))

        expect:
        options.getFetchArgument([] as Set) == [:]
        options.getFetchArgument(['author', 'publisher'] as Set) == [fetch: [author: 'join', publisher: 'join']]
    }

    void 'the environment selections are inspected through the merged field'() {
        given:
        EntityFetchOptions options = new EntityFetchOptions(context.getPersistentEntity(EfoAuthor.name))
        Field withSelections = field('author', field('name'), field('books', field('title')))
        Field withoutSelections = field('author')
        Field withFragment = Field.newField('author').selectionSet(SelectionSet.newSelectionSet().selection(FragmentSpread.newFragmentSpread('frag').build()).build()).build()
        DataFetchingEnvironment environment = Mock(DataFetchingEnvironment) {
            getMergedField() >> MergedField.newMergedField([withSelections, withoutSelections, withFragment]).build()
        }
        DataFetchingEnvironment empty = Mock(DataFetchingEnvironment) {
            getMergedField() >> null
        }

        expect:
        options.getJoinProperties(environment) == ['books'] as Set
        options.getJoinProperties(environment, true) == [] as Set
        options.getFetchArgument(environment) == [fetch: [books: 'join']]
        options.getFetchArgument(environment, true) == [:]
        options.getJoinProperties(empty) == [] as Set
    }

}

@Entity
class EfoAuthor {

    Long id
    String name
    Set<EfoBook> books
    static hasMany = [books: EfoBook]

}

@Entity
class EfoBook {

    Long id
    String title
    EfoAuthor author
    EfoPublisher publisher
    static belongsTo = [author: EfoAuthor]

}

@Entity
class EfoPublisher {

    Long id
    String name

}
