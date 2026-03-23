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
package org.grails.orm.hibernate.cfg.domainbinding

import org.hibernate.type.descriptor.WrapperOptions

import grails.gorm.specs.HibernateGormDatastoreSpec
import grails.persistence.Entity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateBasicProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateEnumProperty
import org.grails.orm.hibernate.cfg.IdentityEnumType
import jakarta.persistence.EnumType
import org.grails.orm.hibernate.cfg.domainbinding.binder.GrailsDomainBinder
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty
import org.grails.orm.hibernate.cfg.domainbinding.util.BackticksRemover
import org.grails.orm.hibernate.cfg.domainbinding.util.ColumnNameForPropertyAndPathFetcher
import org.grails.orm.hibernate.cfg.domainbinding.util.DefaultColumnNameFetcher
import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.hibernate.mapping.Table
import org.hibernate.mapping.RootClass
import org.hibernate.usertype.UserType
import spock.lang.Subject
import spock.lang.Unroll

import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException

import org.grails.orm.hibernate.cfg.domainbinding.binder.ColumnConfigToColumnBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.EnumTypeBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.IndexBinder
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity

class EnumTypeBinderSpec extends HibernateGormDatastoreSpec {

    def indexBinder = Mock(IndexBinder)
    def columnBinder = Mock(ColumnConfigToColumnBinder)

    @Subject
    EnumTypeBinder binder

    def setup() {
        def grailsDomainBinder = getGrailsDomainBinder()
        def metadataBuildingContext = grailsDomainBinder.getMetadataBuildingContext()
        def namingStrategy = grailsDomainBinder.getNamingStrategy()
        def defaultColumnNameFetcher = new DefaultColumnNameFetcher(namingStrategy, new BackticksRemover())
        def columnNameFetcher = new ColumnNameForPropertyAndPathFetcher(namingStrategy, defaultColumnNameFetcher, new BackticksRemover())
        binder = new EnumTypeBinder(metadataBuildingContext, columnNameFetcher, indexBinder, columnBinder, namingStrategy)
    }

    private PersistentProperty setupProperty(Class clazz, String propertyName, Table table) {
        def grailsDomainBinder = getGrailsDomainBinder()
        def owner = createPersistentEntity(clazz, grailsDomainBinder) as GrailsHibernatePersistentEntity

        def rootClass = new RootClass(grailsDomainBinder.getMetadataBuildingContext())
        rootClass.setTable(table)
        owner.setPersistentClass(rootClass)

        return owner.getPropertyByName(propertyName)
    }

    def "should bind enum type for a collection element"() {
        given: "An entity with a collection of enums"
        def table = new Table("person_statuses")
        def property = setupProperty(PersonWithCollection, "statuses", table)

        expect: "The property is a ToMany property"
        property instanceof HibernateBasicProperty == true

        when: "the enum is bound for the collection column"
        // This will now successfully call property.getComponentType() internally
        def result = binder.bindEnumTypeForColumn(property as HibernateBasicProperty)

        then: "The BasicValue is configured correctly"
        result.getEnumerationStyle() == EnumType.STRING
        result.getTypeParameters().getProperty(GrailsDomainBinder.ENUM_CLASS_PROP) == Status01.name
    }

    @Unroll
    def "should bind enum type as #expectedHibernateType when mapping specifies enumType as '#enumTypeMapping'"() {
        given: "A root entity and its enum property"
        def table = new Table("person")
        def property = setupProperty(clazz, "status", table)

        when: "the enum is bound via the standard path"
        def simpleValue = binder.bindEnumType(property as HibernateEnumProperty, "")

        then: "the correct hibernate type is set"
        simpleValue.getTypeName() == expectedHibernateType
        simpleValue.getEnumerationStyle() == expectedEnumStyle
        simpleValue.isNullable() == nullable

        where:
        clazz    | enumTypeMapping  | expectedHibernateType            | expectedEnumStyle | nullable
        Person01 | "default"        | null                             | EnumType.STRING   | false
        Person02 | "string"         | null                             | EnumType.STRING   | true
        Person03 | "ordinal"        | null                             | EnumType.ORDINAL  | true
        Person04 | "identity"       | IdentityEnumType.class.getName() | null              | false
        Person05 | UserTypeEnumType | UserTypeEnumType.class.getName() | null              | false
    }

    @Unroll
    def "should set column nullability"() {
        given: "A root entity and its enum property"
        def table = new Table("person")
        def property = setupProperty(clazz, "status", table)

        when: "the enum is bound"
        def simpleValue = binder.bindEnumType(property as HibernateEnumProperty, "")

        then:
        simpleValue.getColumns()[0].isNullable() == nullable

        where:
        clazz    | nullable
        Person01 | false
        Person02 | true
        Clown01  | true
    }

    def "should bind enum type with explicit table"() {
        given: "A root entity and its enum property"
        def table = new Table("explicit_table")
        def property = setupProperty(Person01, "status", new Table("internal"))

        when: "the enum is bound with an explicit table"
        def simpleValue = binder.bindEnumType(property as HibernateEnumProperty, table, "myPath")

        then: "the provided table is used instead of the property's internal table"
        simpleValue.getTable() == table
    }
}

// --- Supporting Classes ---

enum Status01 { AVAILABLE, OUT_OF_STOCK }

@Entity class Person01 { Long id; Status01 status }
@Entity class Person02 {
    Long id; Status01 status
    static mapping = { status enumType: "string", nullable: true }
}
@Entity class Person03 {
    Long id; Status01 status
    static mapping = { status enumType: "ordinal", nullable: true }
}
@Entity class Person04 {
    Long id; Status01 status
    static mapping = { status enumType: "identity" }
}
@Entity class Person05 {
    Long id; Status01 status
    static mapping = { status type: UserTypeEnumType }
}
@Entity class PersonWithCollection {
    Long id
    Set<Status01> statuses
}
@Entity class Clown01 extends Person01 { String clownName }

class UserTypeEnumType implements UserType {
    @Override int getSqlType() { 0 }
    @Override Class returnedClass() { Status01 }
    @Override boolean equals(Object x, Object y) { x == y }
    @Override int hashCode(Object x) { x.hashCode() }
    @Override Object nullSafeGet(ResultSet rs, int position, WrapperOptions options) throws SQLException { null }
    @Override void nullSafeSet(PreparedStatement st, Object value, int index, WrapperOptions options) throws SQLException {}
    @Override Object deepCopy(Object value) { value }
    @Override boolean isMutable() { false }
    @Override Serializable disassemble(Object value) { (Serializable)value }
    @Override Object assemble(Serializable cached, Object owner) { cached }
}