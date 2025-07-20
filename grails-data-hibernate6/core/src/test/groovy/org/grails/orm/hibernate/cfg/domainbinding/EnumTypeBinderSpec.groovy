package org.grails.orm.hibernate.cfg.domainbinding

import grails.gorm.specs.HibernateGormDatastoreSpec
import grails.persistence.Entity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.orm.hibernate.cfg.IdentityEnumType
import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.hibernate.mapping.BasicValue
import org.hibernate.mapping.Column
import org.hibernate.mapping.Selectable
import org.hibernate.mapping.Table
import org.hibernate.type.EnumType
import org.hibernate.usertype.UserType
import spock.lang.Subject
import spock.lang.Unroll

import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Types

class EnumTypeBinderSpec extends HibernateGormDatastoreSpec {




    def indexBinder = Mock(IndexBinder)
    def columnBinder = Mock(ColumnConfigToColumnBinder)

    @Subject
    EnumTypeBinder binder = new EnumTypeBinder(indexBinder, columnBinder)



    @Unroll
    def "should bind enum type as #expectedHibernateType when mapping specifies enumType as '#enumTypeMapping'"() {
        given: "A root entity and its enum property"
        def grailsDomainBinder = getGrailsDomainBinder()
        def owner = createPersistentEntity(clazz, grailsDomainBinder)
        PersistentProperty property = owner.getPropertyByName("status")
        def table = new Table("person")
        def simpleValue = new BasicValue(grailsDomainBinder.metadataBuildingContext, table)

        when: "the enum is bound"
        binder.bindEnumType(property, Status01, simpleValue, "status_col")

        then: "the correct hibernate type is set"
        simpleValue.getTypeName() == expectedHibernateType
        simpleValue.isNullable() == nullable

        and: "the type parameters are configured correctly"
        def props = simpleValue.getTypeParameters()
        (props.getProperty(EnumType.TYPE) == String.valueOf(expectedSqlType)) == typeExpected
        (props.getProperty(EnumType.NAMED) == String.valueOf(namedExpected)) == namedIsExpected

        where:
        clazz | enumTypeMapping   | expectedHibernateType            | expectedSqlType   | typeExpected | namedExpected | namedIsExpected | nullable
        Person01| "default"       | EnumType.class.getName()         | Types.VARCHAR     | true         | true          | true            | false
        Person02|"string"         | EnumType.class.getName()         | Types.VARCHAR     | true         | true          | true            | true
        Person03|"ordinal"        | EnumType.class.getName()         | Types.INTEGER     | true         | false         | true            | true
        Person04|"identity"       | IdentityEnumType.class.getName() | null              | false        | null          | false           | false
        Person05|UserTypeEnumType | UserTypeEnumType.class.getName() | null              | false        | null          | false           | false
    }




    @Unroll
    def "should set column nullability "() {
        given: "A root entity and its enum property"
        def grailsDomainBinder = getGrailsDomainBinder()
        def owner = createPersistentEntity( clazz, grailsDomainBinder)
        PersistentProperty property = owner.getPropertyByName("status")
        def table = new Table("person")
        def simpleValue = new BasicValue(grailsDomainBinder.metadataBuildingContext, table)
        def columnName = "status_col"
        when: "the enum is bound"
        binder.bindEnumType(property, Status01, simpleValue, columnName)

        then:
        table.columns.size() == 1
        table.columns[0] == simpleValue.getColumn()
        table.columns[0].isNullable() == nullable
        table.columns[0].getValue() == simpleValue
        table.columns[0].getName() == columnName

        where:
        clazz | nullable
        Person01| false
        Person02| true
        Clown01 | true
        Clown02 | true
        Clown03 | true

    }

    @Unroll
    def "should bind index and column constraints only when a column config is present"() {
        given: "A root entity and its enum property"
        // This test assumes 'indexBinder' and 'columnBinder' are mocked collaborators
        // injected via a setup() block, as they are created inside the binder.
        def grailsDomainBinder = getGrailsDomainBinder()
        def owner = createPersistentEntity(clazz, grailsDomainBinder)
        PersistentProperty property = owner.getPropertyByName("status")
        def table = new Table("person")
        def simpleValue = new BasicValue(grailsDomainBinder.metadataBuildingContext, table)
        def columnName = "status_col"

        when: "the enum is bound"
        binder.bindEnumType(property, Status01, simpleValue, columnName)

        then: "the index and column binders are invoked the correct number of times"
        times * indexBinder.bindIndex(columnName, _ as Column, _, table)
        times * columnBinder.bindColumnConfigToColumn(_ as Column, _, _)

        where:
        clazz    | times
        Person01 | 0
        Person02 | 1
    }



}

class UserTypeEnumType implements UserType {

    @Override
    int getSqlType() {
        return 0
    }

    @Override
    Class returnedClass() {
        return null
    }

    @Override
    boolean equals(Object x, Object y) {
        return false
    }

    @Override
    int hashCode(Object x) {
        return 0
    }

    @Override
    Object nullSafeGet(ResultSet rs, int position, SharedSessionContractImplementor session, @Deprecated Object owner) throws SQLException {
        return null
    }

    @Override
    void nullSafeSet(PreparedStatement st, Object value, int index, SharedSessionContractImplementor session) throws SQLException {

    }

    @Override
    Object deepCopy(Object value) {
        return null
    }

    @Override
    boolean isMutable() {
        return false
    }

    @Override
    Serializable disassemble(Object value) {
        return null
    }

    @Override
    Object assemble(Serializable cached, Object owner) {
        return null
    }
}

enum Status01 {
    AVAILABLE, OUT_OF_STOCK
}

enum Status02 {
    FOO(3), BAR(5)
    Long id
    Status02(Long id) {
        this.id = id
    }
}

@Entity
class Person01 {
    Long id
    Status01 status
}

@Entity
class Person02 {
    Long id
    Status01 status
    static mapping = {
        status enumType: "string", nullable :true

    }
}

@Entity
class Person03 {
    Long id
    Status01 status
    static mapping = {
        status enumType: "ordinal", nullable :true
        tablePerHierarchy false

    }
}

@Entity
class Person04 {
    Long id
    Status02 status
    static mapping = {
        status enumType: 'identity'
    }
}

@Entity
class Person05 {
    Long id
    Status02 status
    static mapping = {
        status type: UserTypeEnumType
    }
}

@Entity
class Clown01 extends Person01 {
    String clownName
}

@Entity
class Clown02 extends Person02 {
    String clownName
}

@Entity
class Clown03 extends Person03 {
    String clownName
}