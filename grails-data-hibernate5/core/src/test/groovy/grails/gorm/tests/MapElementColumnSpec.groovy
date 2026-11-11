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
package grails.gorm.tests

import grails.gorm.annotation.Entity
import org.grails.orm.hibernate.HibernateDatastore
import org.hibernate.mapping.Collection
import org.hibernate.mapping.Column
import org.hibernate.mapping.IndexedCollection
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

/**
 * The element column of a basic collection takes the length that the mapping gives the property: a map of values gets
 * the same length as a list of values, and as a plain string property.
 */
@Issue('https://github.com/apache/grails-core/issues/16897')
class MapElementColumnSpec extends Specification {

    @Shared @AutoCleanup HibernateDatastore datastore = new HibernateDatastore(
            MapEltPlain, MapEltText, MapEltMaxSize, MapEltJoinColumn, ListEltText, ListEltMaxSize
    )

    private Column elementColumn(Class<?> type, String property) {
        Collection collection = datastore.metadata.getCollectionBinding(type.name + '.' + property)
        collection.element.columnIterator.next() as Column
    }

    private Column indexColumn(Class<?> type, String property) {
        ((IndexedCollection) datastore.metadata.getCollectionBinding(type.name + '.' + property)).index.columnIterator.next() as Column
    }

    void "a map of strings keeps the default length without a mapping"() {
        expect:
        elementColumn(MapEltPlain, 'body').length == Column.DEFAULT_LENGTH
    }

    @Unroll
    void "maxSize gives the element column of a #label the length it gives a string property"() {
        expect:
        elementColumn(type, 'body').length == 500

        where:
        label  | type
        'map'  | MapEltMaxSize
        'list' | ListEltMaxSize
    }

    @Unroll
    void "type: 'text' maps the elements of a #label as text"() {
        expect:
        elementColumn(type, 'body').value.typeName == 'text'

        where:
        label  | type
        'map'  | MapEltText
        'list' | ListEltText
    }

    void "a join table column configuration names the element column of a map"() {
        expect:
        elementColumn(MapEltJoinColumn, 'body').name == 'body_value'
        elementColumn(MapEltJoinColumn, 'body').length == Column.DEFAULT_LENGTH
    }

    void "the key column of a map is not changed by the mapping of its values"() {
        expect:
        indexColumn(MapEltText, 'body').length == Column.DEFAULT_LENGTH
        indexColumn(MapEltMaxSize, 'body').length == Column.DEFAULT_LENGTH
    }
}

@Entity
class MapEltPlain {
    Map<String, String> body
}

@Entity
class MapEltText {
    Map<String, String> body

    static mapping = {
        body type: 'text'
    }
}

@Entity
class MapEltMaxSize {
    Map<String, String> body

    static constraints = {
        body maxSize: 500
    }
}

@Entity
class MapEltJoinColumn {
    Map<String, String> body

    static mapping = {
        body joinTable: [column: 'body_value']
    }
}

@Entity
class ListEltText {
    static hasMany = [body: String]

    static mapping = {
        body type: 'text'
    }
}

@Entity
class ListEltMaxSize {
    static hasMany = [body: String]

    static constraints = {
        body maxSize: 500
    }
}
