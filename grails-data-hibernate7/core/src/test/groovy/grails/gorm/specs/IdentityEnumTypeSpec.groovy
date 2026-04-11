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
package grails.gorm.specs

import grails.gorm.annotation.Entity
import grails.gorm.transactions.Rollback
import jakarta.persistence.Enumerated
import jakarta.persistence.EnumType
import org.grails.orm.hibernate.cfg.IdentityEnumType
import org.hibernate.MappingException

import javax.sql.DataSource
import java.sql.ResultSet

/**
 * Created by graemerocher on 16/11/16.
 */
class IdentityEnumTypeSpec extends HibernateGormDatastoreSpec {

    def setupSpec() {
        manager.addAllDomainClasses([EnumEntityDomain, FooWithEnum])
    }

    @Rollback
    void "test identity enum type"() {
        when:
        new EnumEntityDomain(status: EnumEntityDomain.Status.FOO).save(flush: true)
        DataSource ds = manager.hibernateDatastore.connectionSources.defaultConnectionSource.dataSource
        ResultSet resultSet = ds.getConnection().prepareStatement('select status from enum_entity_domain').executeQuery()

        then:
        resultSet.next()
        resultSet.getString(1) == 'FOO'
        EnumEntityDomain.first().status == EnumEntityDomain.Status.FOO
    }

    @Rollback
    void "test identity enum type 2"() {
        when:
        new FooWithEnum(name: "blah", mySuperValue: XEnum.X__TWO).save(flush: true)
        DataSource ds = manager.hibernateDatastore.connectionSources.defaultConnectionSource.dataSource
        ResultSet resultSet = ds.getConnection().prepareStatement('select my_super_value from foo_with_enum').executeQuery()

        then:
        resultSet.next()
        resultSet.getString(1) == "X__TWO"
        FooWithEnum.first().mySuperValue == XEnum.X__TWO
    }

    // ── Direct unit tests for IdentityEnumType ────────────────────────────────

    def "setParameterValues initializes enumClass and bidiMap"() {
        given:
        def type = new IdentityEnumType()
        def props = new Properties()
        props.setProperty(IdentityEnumType.PARAM_ENUM_CLASS, IdentityStatusEnum.name)

        when:
        type.setParameterValues(props)

        then:
        type.returnedClass() == IdentityStatusEnum
        type.getSqlTypes() != null
        type.getSqlTypes().length > 0
    }

    def "setParameterValues throws MappingException for enum without getId method"() {
        given:
        def type = new IdentityEnumType()
        def props = new Properties()
        props.setProperty(IdentityEnumType.PARAM_ENUM_CLASS, PlainEnum.name)

        when:
        type.setParameterValues(props)

        then:
        thrown(MappingException)
    }

    def "getBidiEnumMap caches the same instance across calls"() {
        when:
        def map1 = IdentityEnumType.getBidiEnumMap(IdentityStatusEnum)
        def map2 = IdentityEnumType.getBidiEnumMap(IdentityStatusEnum)

        then:
        map1.is(map2)
    }

    def "equals uses identity comparison"() {
        given:
        def type = new IdentityEnumType()

        expect:
        type.equals(IdentityStatusEnum.ACTIVE, IdentityStatusEnum.ACTIVE)
        !type.equals(IdentityStatusEnum.ACTIVE, IdentityStatusEnum.INACTIVE)
        !type.equals(null, IdentityStatusEnum.ACTIVE)
    }

    def "hashCode delegates to the object"() {
        given:
        def type = new IdentityEnumType()
        def val = IdentityStatusEnum.ACTIVE

        expect:
        type.hashCode(val) == val.hashCode()
    }

    def "deepCopy returns the same object reference"() {
        given:
        def type = new IdentityEnumType()
        def val = IdentityStatusEnum.ACTIVE

        expect:
        type.deepCopy(val).is(val)
    }

    def "isMutable returns false"() {
        expect:
        !new IdentityEnumType().isMutable()
    }

    def "disassemble returns the value as Serializable"() {
        given:
        def type = new IdentityEnumType()
        def val = IdentityStatusEnum.ACTIVE

        expect:
        type.disassemble(val).is(val)
    }

    def "assemble returns the cached value unchanged"() {
        given:
        def type = new IdentityEnumType()
        def val = IdentityStatusEnum.ACTIVE

        expect:
        type.assemble(val, null).is(val)
    }

    def "replace returns the original value"() {
        given:
        def type = new IdentityEnumType()

        expect:
        type.replace(IdentityStatusEnum.ACTIVE, IdentityStatusEnum.INACTIVE, null).is(IdentityStatusEnum.ACTIVE)
    }

    def "getBidiEnumMap logs warning for duplicate enum ids and still returns a map"() {
        when:
        def map = IdentityEnumType.getBidiEnumMap(DuplicateIdEnum)

        then:
        noExceptionThrown()
        map != null
    }

    def "getSqlType returns 0"() {
        expect:
        new IdentityEnumType().getSqlType() == 0
    }

    def "getDefaultSqlLength returns without throwing"() {
        expect:
        new IdentityEnumType().getDefaultSqlLength() >= -1
    }

    def "getDefaultSqlPrecision returns without throwing"() {
        expect:
        new IdentityEnumType().getDefaultSqlPrecision() >= -1
    }

    def "getDefaultSqlScale returns without throwing"() {
        expect:
        new IdentityEnumType().getDefaultSqlScale() >= -1
    }

    def "getValueConverter returns null (default UserType behavior)"() {
        expect:
        new IdentityEnumType().getValueConverter() == null
    }

    def "BidiEnumMap.getEnumValue looks up enum by id"() {
        given:
        def bidiMap = IdentityEnumType.getBidiEnumMap(IdentityStatusEnum)

        when:
        def result = bidiMap.getEnumValue("A")

        then:
        result == IdentityStatusEnum.ACTIVE
    }

    def "BidiEnumMap.getKey looks up id by enum value"() {
        given:
        def bidiMap = IdentityEnumType.getBidiEnumMap(IdentityStatusEnum)

        when:
        def result = bidiMap.getKey(IdentityStatusEnum.INACTIVE)

        then:
        result == "I"
    }

    def "BidiEnumMap.getEnumValue returns null for unknown id"() {
        given:
        def bidiMap = IdentityEnumType.getBidiEnumMap(IdentityStatusEnum)

        expect:
        bidiMap.getEnumValue("UNKNOWN") == null
    }

    def "getSqlTypes returns null if not initialized"() {
        expect:
        new IdentityEnumType().getSqlTypes() == null
    }

    def "returnedClass returns null if not initialized"() {
        expect:
        new IdentityEnumType().returnedClass() == null
    }

    def "setParameterValues throws MappingException for non-existent class"() {
        given:
        def type = new IdentityEnumType()
        def props = new Properties()
        props.setProperty(IdentityEnumType.PARAM_ENUM_CLASS, "non.existent.Enum")

        when:
        type.setParameterValues(props)

        then:
        thrown(MappingException)
    }
}

@Entity
class EnumEntityDomain {
    @Enumerated(EnumType.STRING)
    Status status

    static mapping = {
        status(enumType: "string")
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
    @Enumerated(EnumType.STRING)
    XEnum mySuperValue

    static mapping = {
        version false
        mySuperValue enumType: "string"
    }
}

enum XEnum {
    X__ONE(000, "x.one"),
    X__TWO(100, "x.two"),
    X__THREE(200, "x.three")

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

/** Enum with a String id — used for direct IdentityEnumType unit tests. */
enum IdentityStatusEnum {
    ACTIVE("A"), INACTIVE("I")
    final String id
    IdentityStatusEnum(String id) { this.id = id }
}

/** Plain enum with no getId — should cause MappingException in setParameterValues. */
enum PlainEnum {
    ONE, TWO
}

/** Enum with duplicate ids — triggers the warn path in BidiEnumMap. */
enum DuplicateIdEnum {
    X("same"), Y("same")
    final String id
    DuplicateIdEnum(String id) { this.id = id }
}
