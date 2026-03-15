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

import grails.gorm.specs.HibernateGormDatastoreSpec
import grails.persistence.Entity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateEnumProperty
import org.grails.orm.hibernate.cfg.IdentityEnumType
import jakarta.persistence.EnumType
import org.grails.orm.hibernate.cfg.domainbinding.binder.GrailsDomainBinder
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty
import org.grails.orm.hibernate.cfg.domainbinding.util.BackticksRemover
import org.grails.orm.hibernate.cfg.domainbinding.util.ColumnNameForPropertyAndPathFetcher
import org.grails.orm.hibernate.cfg.domainbinding.util.DefaultColumnNameFetcher
import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.hibernate.mapping.BasicValue
import org.hibernate.mapping.Column
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
        binder = new EnumTypeBinder(metadataBuildingContext, columnNameFetcher, indexBinder, columnBinder)
    }

    /**
     * Helper to prevent the NullPointerException by linking the GORM entity
     * to a Hibernate RootClass/Table.
     */
    private PersistentProperty setupEntity(Class clazz, Table table) {
        def grailsDomainBinder = getGrailsDomainBinder()
        def owner = createPersistentEntity(clazz, grailsDomainBinder) as GrailsHibernatePersistentEntity

        def rootClass = new RootClass(grailsDomainBinder.getMetadataBuildingContext())
        rootClass.setTable(table)
        owner.setPersistentClass(rootClass)

        return owner.getPropertyByName("status")
    }

    @Unroll
    def "should bind enum type as #expectedHibernateType when mapping specifies enumType as '#enumTypeMapping'"() {
        given: "A root entity and its enum property"
        def table = new Table("person")
        PersistentProperty property = setupEntity(clazz, table)

        when: "the enum is bound"
        def simpleValue = binder.bindEnumTypeForColumn(property as HibernateToManyProperty, Status01, table, "status_col")

        then: "the correct hibernate type is set"
        simpleValue.getTypeName() == expectedHibernateType
        simpleValue.getEnumerationStyle() == expectedEnumStyle
        simpleValue.isNullable() == nullable

        and: "the enum class property is always set"
        simpleValue.getTypeParameters().getProperty(GrailsDomainBinder.ENUM_CLASS_PROP) == Status01.name

        where:
        clazz    | enumTypeMapping  | expectedHibernateType                   | expectedEnumStyle | nullable
        Person01 | "default"        | null                                    | EnumType.STRING   | false
        Person02 | "string"         | null                                    | EnumType.STRING   | true
        Person03 | "ordinal"        | null                                    | EnumType.ORDINAL  | true
        Person04 | "identity"       | IdentityEnumType.class.getName()        | null              | false
        Person05 | UserTypeEnumType | UserTypeEnumType.class.getName()        | null              | false
    }

    @Unroll
    def "should set column nullability"() {
        given: "A root entity and its enum property"
        def table = new Table("person")
        def columnName = "status_col"
        PersistentProperty property = setupEntity(clazz, table)

        when: "the enum is bound"
        def simpleValue = binder.bindEnumTypeForColumn(property as HibernateToManyProperty, Status01, table, columnName)

        then:
        table.columns.size() == 1
        table.columns[0] == simpleValue.getColumn()
        table.columns[0].isNullable() == nullable
        table.columns[0].getValue() == simpleValue
        table.columns[0].getName() == columnName

        where:
        clazz    | nullable
        Person01 | false
        Person02 | true
        Clown01  | true
        Clown02  | true
        Clown03  | true
    }

    @Unroll
    def "should bind index and column constraints only when a column config is present"() {
        given: "A root entity and its enum property"
        def table = new Table("person")
        def columnName = "status_col"
        PersistentProperty property = setupEntity(clazz, table)

        when: "the enum is bound"
        binder.bindEnumTypeForColumn(property as HibernateToManyProperty, Status01, table, columnName)

        then: "the index and column binders are invoked the correct number of times"
        times * indexBinder.bindIndex(columnName, _ as Column, _, table)
        times * columnBinder.bindColumnConfigToColumn(_ as Column, _, _)

        where:
        clazz    | times
        Person01 | 0
        Person02 | 1
    }

    def "should create BasicValue and bind enum type"() {
        given: "A root entity and its enum property"
        def table = new Table("person")
        PersistentProperty property = setupEntity(Person01, table)

        when: "the enum is bound using the new signature"
        def result = binder.bindEnumType(property as HibernateEnumProperty, Status01, table, "")

        then: "a BasicValue is returned and bound correctly"
        result instanceof BasicValue
        result.getTable() == table
        result.getTypeName() == null
        result.getEnumerationStyle() == EnumType.STRING
        result.getColumns().size() == 1
        result.getColumns()[0].getName() == "status"
    }
}

// --- Supporting Classes ---

class UserTypeEnumType implements UserType {
    @Override int getSqlType() { 0 }
    @Override Class returnedClass() { null }
    @Override boolean equals(Object x, Object y) { false }
    @Override int hashCode(Object x) { 0 }
    @Override Object nullSafeGet(ResultSet rs, int position, SharedSessionContractImplementor session, Object owner) throws SQLException { null }
    @Override void nullSafeSet(PreparedStatement st, Object value, int index, SharedSessionContractImplementor session) throws SQLException {}
    @Override Object deepCopy(Object value) { null }
    @Override boolean isMutable() { false }
    @Override Serializable disassemble(Object value) { null }
    @Override Object assemble(Serializable cached, Object owner) { null }
}

enum Status01 { AVAILABLE, OUT_OF_STOCK }

enum Status02 {
    FOO(3), BAR(5)
    Long id
    Status02(Long id) { this.id = id }
}

@Entity class Person01 { Long id; Status01 status }
@Entity class Person02 {
    Long id; Status01 status
    static mapping = { status enumType: "string", nullable: true }
}
@Entity class Person03 {
    Long id; Status01 status
    static mapping = { status enumType: "ordinal", nullable: true; tablePerHierarchy false }
}
@Entity class Person04 {
    Long id; Status02 status
    static mapping = { status enumType: 'identity' }
}
@Entity class Person05 {
    Long id; Status02 status
    static mapping = { status type: UserTypeEnumType }
}
@Entity class Clown01 extends Person01 { String clownName }
@Entity class Clown02 extends Person02 { String clownName }
@Entity class Clown03 extends Person03 { String clownName }