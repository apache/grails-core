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
import grails.gorm.transactions.Rollback
import org.grails.orm.hibernate.HibernateDatastore
import org.grails.orm.hibernate.cfg.IdentityEnumType
import org.hibernate.HibernateException
import org.hibernate.MappingException
import org.springframework.transaction.PlatformTransactionManager
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.sql.DataSource
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types

/**
 * Created by graemerocher on 16/11/16.
 */
class IdentityEnumTypeSpec extends Specification {

    @Shared @AutoCleanup HibernateDatastore hibernateDatastore = new HibernateDatastore(EnumEntityDomain, FooWithEnum)
    @Shared PlatformTransactionManager transactionManager = hibernateDatastore.getTransactionManager()

    @Rollback
    void "test identity enum type"() {
        when:
        new EnumEntityDomain(status: EnumEntityDomain.Status.FOO).save(flush:true)
        DataSource ds = hibernateDatastore.connectionSources.defaultConnectionSource.dataSource
        ResultSet resultSet = ds.getConnection().prepareStatement('select status from enum_entity_domain').executeQuery()

        then:
        resultSet.next()
        resultSet.getString(1) == 'F'
        EnumEntityDomain.first().status == EnumEntityDomain.Status.FOO
    }

    @Rollback
    void "test identity enum type 2"() {
        when:
        new FooWithEnum(name: "blah", mySuperValue: XEnum.X__TWO).save(flush:true)
        DataSource ds = hibernateDatastore.connectionSources.defaultConnectionSource.dataSource
        ResultSet resultSet = ds.getConnection().prepareStatement('select my_super_value from foo_with_enum').executeQuery()

        then:
        resultSet.next()
        resultSet.getInt(1) == 100
        FooWithEnum.first().mySuperValue == XEnum.X__TWO
    }

    void "test setParameterValues throws a MappingException when the enum class cannot be loaded"() {
        given:
        def type = new IdentityEnumType()
        def props = new Properties()
        props.setProperty(IdentityEnumType.PARAM_ENUM_CLASS, "does.not.Exist")

        when:
        type.setParameterValues(props)

        then:
        def ex = thrown(MappingException)
        ex.message.contains("does.not.Exist")
    }

    void "test setParameterValues falls back to a raw Class attribute when no String enumClass property is set"() {
        given:
        def type = new IdentityEnumType()
        def props = new Properties()
        props.put(IdentityEnumType.PARAM_ENUM_CLASS, XEnum)

        when:
        type.setParameterValues(props)

        then:
        noExceptionThrown()
        type.returnedClass() == XEnum
    }

    void "test setParameterValues throws a MappingException when no enumClass parameter is provided"() {
        given:
        def type = new IdentityEnumType()

        when:
        type.setParameterValues(new Properties())

        then:
        def ex = thrown(MappingException)
        ex.message.contains("enumClass parameter is required")
    }

    void "test getBidiEnumMap wraps reflection failures in a HibernateException"() {
        when:
        IdentityEnumType.getBidiEnumMap(EnumWithoutId)

        then:
        def ex = thrown(HibernateException)
        ex.message.contains("BidiEnumMap")
    }

    void "test getBidiEnumMap logs a warning for duplicate ids but still builds a usable map"() {
        when:
        def map = IdentityEnumType.getBidiEnumMap(EnumWithDuplicateIds)

        then:
        noExceptionThrown()
        map.getEnumValue(1) in [EnumWithDuplicateIds.A, EnumWithDuplicateIds.B]
    }

    void "test setParameterValues selects the BIGINT jdbc type for Long-keyed enums"() {
        given:
        def type = new IdentityEnumType()
        def props = new Properties()
        props.setProperty(IdentityEnumType.PARAM_ENUM_CLASS, EnumWithLongId.name)

        when:
        type.setParameterValues(props)

        then:
        type.getSqlType() == Types.BIGINT
    }

    void "test setParameterValues falls back to VARCHAR jdbc type for a non-standard id type"() {
        given:
        def type = new IdentityEnumType()
        def props = new Properties()
        props.setProperty(IdentityEnumType.PARAM_ENUM_CLASS, EnumWithCustomId.name)

        when:
        type.setParameterValues(props)

        then:
        type.getSqlType() == Types.VARCHAR
    }

    void "test disassemble and assemble round-trip a cached value"() {
        given:
        def type = new IdentityEnumType()

        expect:
        type.disassemble(XEnum.X__TWO) == XEnum.X__TWO
        type.assemble(XEnum.X__TWO, null) == XEnum.X__TWO
    }

    void "test nullSafeSet sets a SQL NULL when the value is null"() {
        given:
        def type = new IdentityEnumType()
        def props = new Properties()
        props.setProperty(IdentityEnumType.PARAM_ENUM_CLASS, XEnum.name)
        type.setParameterValues(props)
        def statement = Mock(PreparedStatement)
        def options = Mock(org.hibernate.type.descriptor.WrapperOptions)

        when:
        type.nullSafeSet(statement, null, 1, options)

        then:
        1 * statement.setNull(1, Types.INTEGER)
    }
}

@Entity
class EnumEntityDomain {
    Status status

    static mapping = {
        status(enumType: "identity")
    }

    enum Status {
        FOO("F"), BAR("B")
        String id
        Status(String id) { this.id = id }
    }
}

@Entity
class FooWithEnum {
    long id
    String name
    XEnum mySuperValue

    static mapping = {
        version false
        mySuperValue enumType:"identity"
    }
}

enum XEnum {
    X__ONE (000, "x.one"),
    X__TWO (100, "x.two"),
    X__THREE (200, "x.three")

    final int id
    final String name

    private XEnum(int id, String name) {
        this.id = id
        this.name = name
    }

    String toString() {
        name
    }
}

enum EnumWithoutId {
    A, B
}

enum EnumWithDuplicateIds {
    A(1), B(1)

    final int id

    EnumWithDuplicateIds(int id) { this.id = id }
}

enum EnumWithLongId {
    A(1L), B(2L)

    final Long id

    EnumWithLongId(Long id) { this.id = id }
}

enum EnumWithCustomId {
    A('x' as Character), B('y' as Character)

    final Character id

    EnumWithCustomId(Character id) { this.id = id }
}
