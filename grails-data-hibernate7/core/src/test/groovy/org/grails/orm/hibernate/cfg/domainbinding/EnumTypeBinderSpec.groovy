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

import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException

import jakarta.persistence.EnumType

import org.hibernate.mapping.RootClass
import org.hibernate.mapping.Table
import org.hibernate.type.descriptor.WrapperOptions
import org.hibernate.usertype.UserType
import spock.lang.Subject
import spock.lang.Unroll

import grails.gorm.tests.HibernateGormDatastoreSpec
import grails.persistence.Entity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.orm.hibernate.cfg.IdentityEnumType
import org.grails.orm.hibernate.cfg.domainbinding.binder.ColumnConfigToColumnBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.EnumTypeBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.GrailsDomainBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.IndexBinder
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateBasicProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateEnumProperty
import org.grails.orm.hibernate.cfg.domainbinding.util.BackticksRemover
import org.grails.orm.hibernate.cfg.domainbinding.util.ColumnNameForPropertyAndPathFetcher
import org.grails.orm.hibernate.cfg.domainbinding.util.CreateKeyForProps
import org.grails.orm.hibernate.cfg.domainbinding.util.DefaultColumnNameFetcher

class EnumTypeBinderSpec extends HibernateGormDatastoreSpec {

    def indexBinder = Mock(IndexBinder)
    def columnBinder = Mock(ColumnConfigToColumnBinder)

    @Subject
    EnumTypeBinder binder

    void setupSpec() {
        manager.registerDomainClasses(PersonWithColumnExtras)
    }

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

        expect: "The property is both a ToMany and an enum property"
        property instanceof HibernateBasicProperty == true
        property instanceof HibernateEnumProperty == true

        when: "the enum is bound for the collection column"
        // This will now successfully call property.getComponentType() internally
        def result = binder.bindEnumType(property as HibernateEnumProperty, GrailsDomainBinder.EMPTY_PATH)

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
        def simpleValue = binder.bindEnumType(property as HibernateEnumProperty,  "myPath")

        then: "the provided table is used instead of the property's internal table"
        simpleValue.getTable() == table
    }

    def "should bind explicit column for enum"() {
        given:
        def table = new Table("person")
        def property = setupProperty(PersonWithExplicitColumn, "status", table)

        when:
        def simpleValue = binder.bindEnumType(property as HibernateEnumProperty, "")

        then:
        1 * columnBinder.bindColumnConfigToColumn(_, _, _)
        1 * indexBinder.bindIndex("status_col", _, _, _)
    }

    def "should apply the comment, default value and read and write expressions of the column config"() {
        given:
        def table = new Table("person")
        def property = setupProperty(PersonWithColumnExtras, "status", table)

        when:
        def column = binder.bindEnumType(property as HibernateEnumProperty, "").getColumns()[0] as org.hibernate.mapping.Column

        then:
        column.getComment() == "the status"
        column.getDefaultValue() == "'AVAILABLE'"
        column.getCustomRead() == "lower(status)"
        column.getCustomWrite() == "upper(?)"
    }

    def "should leave the column extras unset when the enum property has no column config"() {
        given:
        def table = new Table("person")
        def property = setupProperty(Person01, "status", table)

        when:
        def column = binder.bindEnumType(property as HibernateEnumProperty, "").getColumns()[0] as org.hibernate.mapping.Column

        then:
        column.getComment() == null
        column.getDefaultValue() == null
        column.getCustomRead() == null
        column.getCustomWrite() == null
    }

    def "the column extras of an enum property reach the bound column and the schema"() {
        given:
        def column = getPersistentEntity(PersonWithColumnExtras).persistentClass.getProperty("status").columns[0] as org.hibernate.mapping.Column
        def metadata = sessionFactory.currentSession
                .createNativeQuery("select column_default, remarks from information_schema.columns " +
                        "where table_name = 'PERSON_WITH_COLUMN_EXTRAS' and column_name = 'STATUS'", Object[])
                .uniqueResult() as Object[]

        expect:
        column.getComment() == "the status"
        column.getDefaultValue() == "'AVAILABLE'"
        column.getCustomRead() == "lower(status)"
        column.getCustomWrite() == "upper(?)"
        metadata[0].toString().contains("AVAILABLE")
        metadata[1] == "the status"
    }

    def "should create the unique group key for the enum column on the bound table"() {
        given:
        def grailsDomainBinder = getGrailsDomainBinder()
        def namingStrategy = grailsDomainBinder.getNamingStrategy()
        def defaultColumnNameFetcher = new DefaultColumnNameFetcher(namingStrategy, new BackticksRemover())
        def columnNameFetcher = new ColumnNameForPropertyAndPathFetcher(namingStrategy, defaultColumnNameFetcher, new BackticksRemover())
        def createKeyForProps = Mock(CreateKeyForProps)
        def keyBinder = new EnumTypeBinder(grailsDomainBinder.getMetadataBuildingContext(), columnNameFetcher,
                indexBinder, columnBinder, namingStrategy, createKeyForProps)
        def table = new Table("person")
        def property = setupProperty(Person01, "status", table)

        when:
        keyBinder.bindEnumType(property as HibernateEnumProperty, "myPath")

        then:
        1 * createKeyForProps.createKeyForProps(property, "myPath", table, "my_path_status")
    }
}

// --- Supporting Classes ---

enum Status01 { AVAILABLE, OUT_OF_STOCK }

@Entity class Person01 {
    Long id; Status01 status
    static constraints = { status nullable: false }
}
@Entity class Person02 {
    Long id; Status01 status
    static mapping = { status enumType: "string" }
}
@Entity class Person03 {
    Long id; Status01 status
    static mapping = { status enumType: "ordinal" }
}
@Entity class Person04 {
    Long id; Status01 status
    static mapping = { status enumType: "identity", nullable: false }
}
@Entity class Person05 {
    Long id; Status01 status
    static mapping = { status type: UserTypeEnumType, nullable: false }
}
@Entity class PersonWithCollection {
    Long id
    Set<Status01> statuses
}
@Entity class PersonWithExplicitColumn {
    Long id; Status01 status
    static mapping = { status column: "status_col", index: "idx_status" }
}
@Entity class PersonWithColumnExtras {
    Long id; Status01 status
    static mapping = {
        status comment: "the status", defaultValue: "'AVAILABLE'", read: "lower(status)", write: "upper(?)"
    }
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
