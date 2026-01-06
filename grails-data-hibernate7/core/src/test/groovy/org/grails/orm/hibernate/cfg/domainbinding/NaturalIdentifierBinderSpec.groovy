package org.grails.orm.hibernate.cfg.domainbinding

import org.grails.orm.hibernate.cfg.Identity
import org.grails.orm.hibernate.cfg.Mapping
import org.grails.orm.hibernate.cfg.NaturalId
import org.hibernate.MappingException
import org.hibernate.mapping.Column
import org.hibernate.mapping.Property
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.Table
import org.hibernate.mapping.UniqueKey
import org.hibernate.mapping.Value
import spock.lang.Specification
import spock.lang.Unroll

class NaturalIdentifierBinderSpec extends Specification {

    @Unroll("test bindNaturalIdentifier with a single property and mutable=#isMutable")
    void "test bindNaturalIdentifier with a single property"(boolean isMutable) {
        given:
        def mapping = Mock(Mapping.class)
        def identity = Mock(Identity)
        def naturalId = Mock(NaturalId)
        def property = Mock(Property)
        def value = Mock(Value)
        Table table = Mock(Table)
        def id1 = "id1"
        mapping.getIdentity() >> identity
        identity.getNatural() >> naturalId
        naturalId.getPropertyNames() >> [id1]
        naturalId.isMutable() >> isMutable
        def rootClass = Mock(RootClass)
        rootClass.getProperty(id1) >> property
        rootClass.getTable() >> table
        property.getValue() >> value
        value.getSelectables() >> []
        def uniqueNameGenerator = Mock(UniqueNameGenerator)
        def binder = new NaturalIdentifierBinder(uniqueNameGenerator)

        when:
        binder.bindNaturalIdentifier(mapping, rootClass)

        then:
        1 * uniqueNameGenerator.setGeneratedUniqueName(_)
        1 * property.setNaturalIdentifier(true)
        1 * property.setUpdateable(isMutable)
        1 * table.addUniqueKey(_)

        where:
        isMutable << [true, false]
    }

    void "test bindNaturalIdentifier with a composite (multi-property) natural id"() {
        given:
        def mapping = Mock(Mapping.class)
        def identity = Mock(Identity)
        def naturalId = Mock(NaturalId)
        def property1 = Mock(Property)
        def value1 = Mock(Value)
        def column1 = Mock(Column, name: 'id1')
        def property2 = Mock(Property)
        def value2 = Mock(Value)
        def column2 = Mock(Column, name: 'id2')
        Table table = Mock(Table)

        mapping.getIdentity() >> identity
        identity.getNatural() >> naturalId
        naturalId.getPropertyNames() >> ["id1", "id2"]
        naturalId.isMutable() >> true

        def rootClass = Mock(RootClass)
        rootClass.getProperty("id1") >> property1
        rootClass.getProperty("id2") >> property2
        rootClass.getTable() >> table

        property1.getValue() >> value1
        value1.getSelectables() >> [column1]
        property2.getValue() >> value2
        value2.getSelectables() >> [column2]

        def uniqueNameGenerator = Mock(UniqueNameGenerator)
        def binder = new NaturalIdentifierBinder(uniqueNameGenerator)

        when:
        binder.bindNaturalIdentifier(mapping, rootClass)

        then:
        1 * property1.setNaturalIdentifier(true)
        1 * property2.setNaturalIdentifier(true)
        1 * table.addUniqueKey(_) >> { uks ->
            def uk = uks.get(0) as UniqueKey
            assert uk.getColumnSpan() == 2
            assert uk.getColumns().get(0) == column1
            assert  uk.getColumns().get(1)  == column2
        }
    }

    void "test bindNaturalIdentifier when no natural id is defined"() {
        given:
        def mapping = Mock(Mapping.class)
        def identity = Mock(Identity)
        def rootClass = Mock(RootClass)
        def uniqueNameGenerator = Mock(UniqueNameGenerator)
        def binder = new NaturalIdentifierBinder(uniqueNameGenerator)

        mapping.getIdentity() >> identity
        identity.getNatural() >> null

        when:
        binder.bindNaturalIdentifier(mapping, rootClass)

        then:
        0 * rootClass._
        0 * uniqueNameGenerator._
    }

    void "test bindNaturalIdentifier when property not found"() {
        given:
        def mapping = Mock(Mapping.class)
        def identity = Mock(Identity)
        def naturalId = Mock(NaturalId)
        def rootClass = Mock(RootClass)
        def uniqueNameGenerator = Mock(UniqueNameGenerator)
        def binder = new NaturalIdentifierBinder(uniqueNameGenerator)
        Table table = Mock(Table)
        mapping.getIdentity() >> identity
        identity.getNatural() >> naturalId
        naturalId.getPropertyNames() >> ["nonExistentProperty"]
        rootClass.getProperty("nonExistentProperty") >> null
        rootClass.getTable() >> table

        when:
        binder.bindNaturalIdentifier(mapping, rootClass)

        then:
        0 * uniqueNameGenerator.setGeneratedUniqueName(_)
        0 * table.addUniqueKey(_)
    }

}