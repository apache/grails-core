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
package org.grails.orm.hibernate

import grails.gorm.annotation.Entity
import grails.gorm.hibernate.HibernateEntity
import grails.gorm.tests.HibernateGormDatastoreSpec
import org.hibernate.Length
import org.hibernate.mapping.Collection
import org.hibernate.mapping.Column
import spock.lang.Issue
import spock.lang.Unroll

/**
 * The column of the values of a map is the column of a value: a length, a type or a column configuration that the
 * mapping gives the property applies to it, as it does for the elements of a list and for a plain string property.
 */
@Issue('https://github.com/apache/grails-core/issues/16897')
class MapElementColumnSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(MapEltPlain, MapEltText, MapEltMaxSize, MapEltJoinColumn,
                ListEltText, ListEltMaxSize)
    }

    private Column elementColumn(Class<?> type, String property) {
        Collection collection = datastore.getMetadata().getCollectionBinding(type.name + '.' + property)
        collection.element.columns.first() as Column
    }

    private Column indexColumn(Class<?> type, String property) {
        ((org.hibernate.mapping.IndexedCollection) datastore.getMetadata().getCollectionBinding(type.name + '.' + property))
                .index.columns.first() as Column
    }

    void "a map of strings keeps the default length without a mapping"() {
        expect:
        elementColumn(MapEltPlain, 'body').length == null
    }

    @Unroll
    void "#label gives the element column of a map the length it gives the elements of a list"() {
        expect:
        elementColumn(mapType, 'body').length == elementColumn(listType, 'body').length
        elementColumn(mapType, 'body').length == expected

        where:
        label                | mapType        | listType      | expected
        "type: 'text'"       | MapEltText     | ListEltText   | Length.LONG32 as Long
        'maxSize'            | MapEltMaxSize  | ListEltMaxSize | 500L
    }

    void "a join table column configuration names the element column of a map"() {
        expect:
        elementColumn(MapEltJoinColumn, 'body').name == 'body_value'
        elementColumn(MapEltJoinColumn, 'body').length == null
    }

    void "the key column of a map is not changed by the mapping of its values"() {
        expect:
        indexColumn(MapEltText, 'body').length == null
        indexColumn(MapEltMaxSize, 'body').length == null
    }
}

@Entity
class MapEltPlain implements HibernateEntity<MapEltPlain> {
    Map<String, String> body
}

@Entity
class MapEltText implements HibernateEntity<MapEltText> {
    Map<String, String> body

    static mapping = {
        body type: 'text'
    }
}

@Entity
class MapEltMaxSize implements HibernateEntity<MapEltMaxSize> {
    Map<String, String> body

    static constraints = {
        body maxSize: 500
    }
}

@Entity
class MapEltJoinColumn implements HibernateEntity<MapEltJoinColumn> {
    Map<String, String> body

    static mapping = {
        body joinTable: [column: 'body_value']
    }
}

@Entity
class ListEltText implements HibernateEntity<ListEltText> {
    static hasMany = [body: String]

    static mapping = {
        body type: 'text'
    }
}

@Entity
class ListEltMaxSize implements HibernateEntity<ListEltMaxSize> {
    static hasMany = [body: String]

    static constraints = {
        body maxSize: 500
    }
}
