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
package org.grails.orm.hibernate.cfg.domainbinding.secondpass

import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Types

import grails.gorm.annotation.Entity
import grails.gorm.tests.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty
import org.hibernate.MappingException
import org.hibernate.mapping.Column
import org.hibernate.mapping.DependantValue
import org.hibernate.type.descriptor.WrapperOptions
import org.hibernate.usertype.UserType
import spock.lang.Subject
import spock.lang.Unroll

class CollectionKeyColumnUpdaterSpec extends HibernateGormDatastoreSpec {

    @Subject
    CollectionKeyColumnUpdater updater

    CollectionKeyBinder collectionKeyBinder = Mock(CollectionKeyBinder)

    void setupSpec() {
        manager.registerDomainClasses(
            CKCUOwnerOne,
            CKCUItemOne,
            CKCUOwnerMany,
            CKCUItemMany1,
            CKCUItemMany2,
            CKCUBasicOwner,
            CKCUSharedTableOwner,
            CKCUSharedTableItem,
            CKCUColumnBoundOwner
        )
    }

    void setup() {
        updater = new CollectionKeyColumnUpdater(collectionKeyBinder)
    }

    private HibernateToManyProperty propertyFor(Class ownerClass, String name = "items") {
        (getPersistentEntity(ownerClass) as GrailsHibernatePersistentEntity).getPropertyByName(name) as HibernateToManyProperty
    }

    def "bind delegates to collectionKeyBinder and forces nullability and updateability"() {
        given:
        def property = propertyFor(CKCUOwnerOne)
        def column = new Column("test_col")
        column.setNullable(false)
        def key = new DependantValue(getGrailsDomainBinder().getMetadataBuildingContext(), null, null)
        key.addColumn(column)
        key.setUpdateable(false)

        when:
        updater.bind(property)

        then:
        1 * collectionKeyBinder.bind(property) >> key
        column.isNullable()
        key.isUpdateable()
    }

    def "bind keeps the key of a basic collection updateable although the owner has several unidirectional collections"() {
        given:
        def property = propertyFor(CKCUBasicOwner, "tags")
        def column = new Column("test_col")
        def key = new DependantValue(getGrailsDomainBinder().getMetadataBuildingContext(), null, null)
        key.addColumn(column)
        key.setUpdateable(false)

        when:
        updater.bind(property)

        then:
        1 * collectionKeyBinder.bind(property) >> key
        key.isUpdateable()
        column.isNullable()
    }

    def "bind keeps the key updateable when unidirectional collections have join tables of their own"() {
        given:
        def property = propertyFor(CKCUOwnerMany, "items1")
        def column = new Column("test_col")
        def key = new DependantValue(getGrailsDomainBinder().getMetadataBuildingContext(), null, null)
        key.addColumn(column)
        key.setUpdateable(false)

        when:
        updater.bind(property)

        then:
        1 * collectionKeyBinder.bind(property) >> key
        key.isUpdateable()
        column.isNullable()
    }

    def "bind keeps the key updateable when unidirectional collections share a join table"() {
        given: "a key Hibernate would write no rows for, the rule of issue 10811 that Hibernate 7 turns into silent data loss"
        def property = propertyFor(CKCUSharedTableOwner, "createUsers")
        def column = new Column("test_col")
        def key = new DependantValue(getGrailsDomainBinder().getMetadataBuildingContext(), null, null)
        key.addColumn(column)
        key.setUpdateable(false)

        when:
        updater.bind(property)

        then:
        1 * collectionKeyBinder.bind(property) >> key
        key.isUpdateable()
        column.isNullable()
    }

    @Unroll
    void "a to-many mapped as #label is bound as a column: getCollection throws rather than returning null"() {
        given: "the owner was bound by the first pass, which gave the property a column and no Hibernate collection"
        def property = propertyFor(CKCUColumnBoundOwner, name)

        expect:
        !HibernateToManyProperty.isBoundAsCollection(property)
        property.hibernateCollection == null

        when:
        property.getCollection()

        then:
        MappingException e = thrown()
        e.message.contains("[${name}]")
        e.message.contains('Call setCollection() first')

        where:
        label            | name
        'serializable'   | 'tags'
        'a user type'    | 'labels'
    }

    void "bind keeps the key updateable next to a column-bound sibling"() {
        given: "an entity collection whose owner also has a serializable and a user-type to-many"
        def property = propertyFor(CKCUColumnBoundOwner, "items")
        def column = new Column("test_col")
        def key = new DependantValue(getGrailsDomainBinder().getMetadataBuildingContext(), null, null)
        key.addColumn(column)
        key.setUpdateable(false)

        when:
        updater.bind(property)

        then:
        1 * collectionKeyBinder.bind(property) >> key
        noExceptionThrown()
        key.isUpdateable()
        column.isNullable()
    }
}

@Entity
class CKCUBasicOwner {
    Long id
    Set<String> tags
    List<Integer> scores
    static hasMany = [tags: String, scores: Integer]
}

@Entity
class CKCUOwnerOne {
    Long id
    static hasMany = [items: CKCUItemOne]
}

@Entity
class CKCUItemOne {
    Long id
}

@Entity
class CKCUOwnerMany {
    Long id
    static hasMany = [items1: CKCUItemMany1, items2: CKCUItemMany2]
}

@Entity
class CKCUItemMany1 {
    Long id
}

@Entity
class CKCUItemMany2 {
    Long id
}

@Entity
class CKCUSharedTableOwner {
    Long id
    static hasMany = [createUsers: CKCUSharedTableItem, updateUsers: CKCUSharedTableItem]
}

@Entity
class CKCUSharedTableItem {
    Long id
}

@Entity
class CKCUColumnBoundOwner {
    Long id
    Set<String> tags
    Set<String> labels
    static hasMany = [items: CKCUItemOne, tags: String, labels: String]
    static mapping = {
        tags type: 'serializable'
        labels type: CKCUTagSetType
    }
}

class CKCUTagSetType implements UserType<Set> {

    @Override
    int getSqlType() {
        Types.VARCHAR
    }

    @Override
    Class<Set> returnedClass() {
        Set
    }

    @Override
    Set nullSafeGet(ResultSet rs, int position, WrapperOptions options) throws SQLException {
        String value = rs.getString(position)
        rs.wasNull() ? null : (value.split(',') as Set)
    }

    @Override
    void nullSafeSet(PreparedStatement st, Set value, int position, WrapperOptions options) throws SQLException {
        if (value == null) {
            st.setNull(position, Types.VARCHAR)
        }
        else {
            st.setString(position, value.toList().sort().join(','))
        }
    }

    @Override
    Set deepCopy(Set value) {
        value == null ? null : new LinkedHashSet(value)
    }

    @Override
    boolean isMutable() {
        true
    }
}
