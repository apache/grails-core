package org.grails.orm.hibernate.cfg.domainbinding

import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.cfg.CompositeIdentity
import org.grails.orm.hibernate.cfg.Identity
import org.grails.orm.hibernate.cfg.Mapping
import org.grails.orm.hibernate.cfg.NaturalId

import org.hibernate.mapping.Column
import org.hibernate.mapping.Property
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.Table
import org.hibernate.mapping.UniqueKey
import org.hibernate.mapping.Value
import spock.lang.Unroll

import org.grails.orm.hibernate.cfg.domainbinding.binder.NaturalIdentifierBinder
import org.grails.orm.hibernate.cfg.domainbinding.util.UniqueNameGenerator

class NaturalIdentifierBinderSpec extends HibernateGormDatastoreSpec {

    @Unroll("test bindNaturalIdentifier with a single property and mutable=#isMutable")
    void "test bindNaturalIdentifier with a single property"(boolean isMutable) {
        given:
        def mapping = Mock(Mapping.class)
        def identity = Mock(Identity)
        def naturalId = Mock(NaturalId)
        def property = new Property()
        property.setName("id1")
        def value = Mock(Value)
        property.setValue(value)
        Table table = Mock(Table)
        def id1 = "id1"
        mapping.getIdentity() >> identity
        identity.getNatural() >> naturalId
        naturalId.getPropertyNames() >> [id1]
        naturalId.isMutable() >> isMutable
        def rootClass = new RootClass(getGrailsDomainBinder().getMetadataBuildingContext())
        rootClass.setIdentifierProperty(property)
        rootClass.setTable(table)
        value.getSelectables() >> []
        value.hasAnyUpdatableColumns() >> isMutable
        def uniqueNameGenerator = Mock(UniqueNameGenerator)
        def binder = new NaturalIdentifierBinder(uniqueNameGenerator)

        when:
        binder.bindNaturalIdentifier(mapping, rootClass)

        then:
        1 * uniqueNameGenerator.setGeneratedUniqueName(_)
        property.isNaturalIdentifier()
        property.isUpdatable() == isMutable
        1 * table.addUniqueKey(_)

        where:
        isMutable << [true, false]
    }

    void "test bindNaturalIdentifier with a composite (multi-property) natural id"() {
        given:
        def mapping = Mock(Mapping.class)
        def identity = Mock(Identity)
        def naturalId = Mock(NaturalId)
        def property1 =new Property()
        property1.setName("id1")
        def value1 = Mock(Value)
        property1.setValue(value1)
        def column1 = new Column('id1')
        def property2 = new Property()
        property2.setName("id2")
        def value2 = Mock(Value)
        property2.setValue(value2)
        def column2 = new Column('id2')
        Table table = Mock(Table)

        mapping.getIdentity() >> identity
        identity.getNatural() >> naturalId
        naturalId.getPropertyNames() >> ["id1", "id2"]
        naturalId.isMutable() >> true

        def rootClass = new RootClass(getGrailsDomainBinder().getMetadataBuildingContext())
        rootClass.setTable(table)
        rootClass.addProperty(property1)
        rootClass.addProperty(property2)
        rootClass.getTable() >> table

        value1.getSelectables() >> [column1]
        value2.getSelectables() >> [column2]

        def uniqueNameGenerator = Mock(UniqueNameGenerator)
        def binder = new NaturalIdentifierBinder(uniqueNameGenerator)

        when:
        binder.bindNaturalIdentifier(mapping, rootClass)

        then:
        property1.isNaturalIdentifier()
        property2.isNaturalIdentifier()
        1 * table.addUniqueKey(_) >> { uks ->
            def uk = uks.get(0) as UniqueKey
            assert uk.getColumnSpan() == 2
            assert uk.getColumns().get(0) == column1
            assert  uk.getColumns().get(1)  == column2
        }
    }

    void "test bindNaturalIdentifier with CompositeIdentity"() {
        given:
        def mapping = Mock(Mapping.class)
        def identity = Mock(CompositeIdentity)
        def naturalId = Mock(NaturalId)
        def property = new Property()
        property.setName("id1")
        def value = Mock(Value)
        property.setValue(value)
        Table table = Mock(Table)
        def id1 = "id1"
        mapping.getIdentity() >> identity
        identity.getNatural() >> naturalId
        naturalId.getPropertyNames() >> [id1]
        naturalId.isMutable() >> false
        def rootClass = new RootClass(getGrailsDomainBinder().getMetadataBuildingContext())
        rootClass.addProperty(property)
        rootClass.setTable(table)
        value.getSelectables() >> []

        def uniqueNameGenerator = Mock(UniqueNameGenerator)
        def binder = new NaturalIdentifierBinder(uniqueNameGenerator)

        when:
        binder.bindNaturalIdentifier(mapping, rootClass)

        then:
        1 * uniqueNameGenerator.setGeneratedUniqueName(_)
        property.isNaturalIdentifier()
        1 * table.addUniqueKey(_)
    }

    void "test bindNaturalIdentifier when no natural id is defined"() {
        given:
        def mapping = Mock(Mapping.class)
        def identity = Mock(Identity)
        def rootClass = new RootClass(getGrailsDomainBinder().getMetadataBuildingContext())
        def uniqueNameGenerator = Mock(UniqueNameGenerator)
        def binder = new NaturalIdentifierBinder(uniqueNameGenerator)

        mapping.getIdentity() >> identity
        identity.getNatural() >> null

        when:
        binder.bindNaturalIdentifier(mapping, rootClass)

        then:
        0 * uniqueNameGenerator._
    }

    void "test bindNaturalIdentifier when property not found"() {
        given:
        def mapping = Mock(Mapping.class)
        def identity = Mock(Identity)
        def naturalId = Mock(NaturalId)
        def rootClass = new RootClass(getGrailsDomainBinder().getMetadataBuildingContext())
        def uniqueNameGenerator = Mock(UniqueNameGenerator)
        def binder = new NaturalIdentifierBinder(uniqueNameGenerator)
        Table table = Mock(Table)
        mapping.getIdentity() >> identity
        identity.getNatural() >> naturalId
        naturalId.getPropertyNames() >> ["nonExistentProperty"]
        rootClass.setTable(table)

        when:
        binder.bindNaturalIdentifier(mapping, rootClass)

        then:
        0 * uniqueNameGenerator.setGeneratedUniqueName(_)
        0 * table.addUniqueKey(_)
    }

}