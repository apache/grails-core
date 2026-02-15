package org.grails.orm.hibernate.cfg.domainbinding

import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.cfg.CompositeIdentity
import org.grails.orm.hibernate.cfg.Mapping
import org.grails.orm.hibernate.cfg.NaturalId
import org.grails.orm.hibernate.cfg.domainbinding.binder.NaturalIdentifierBinder
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateIdentity
import org.grails.orm.hibernate.cfg.domainbinding.util.UniqueNameGenerator
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.Table
import org.hibernate.mapping.UniqueKey

class NaturalIdentifierBinderSpec extends HibernateGormDatastoreSpec {

    void "test bindNaturalIdentifier calls NaturalId.createUniqueKey and handles result"() {
        given:
        def mapping = Mock(Mapping)
        def identity = Mock(HibernateIdentity)
        def naturalId = Mock(NaturalId)
        def uk = Mock(UniqueKey)
        def table = Mock(Table)
        def rootClass = new RootClass(getGrailsDomainBinder().getMetadataBuildingContext())
        rootClass.setTable(table)
        def uniqueNameGenerator = Mock(UniqueNameGenerator)
        def binder = new NaturalIdentifierBinder(uniqueNameGenerator)

        mapping.getIdentity() >> identity
        identity.getNatural() >> naturalId
        naturalId.createUniqueKey(rootClass) >> Optional.of(uk)

        when:
        binder.bindNaturalIdentifier(mapping, rootClass)

        then:
        1 * uniqueNameGenerator.setGeneratedUniqueName(uk)
        1 * table.addUniqueKey(uk)
    }

    void "test bindNaturalIdentifier when NaturalId returns empty result"() {
        given:
        def mapping = Mock(Mapping)
        def identity = Mock(HibernateIdentity)
        def naturalId = Mock(NaturalId)
        def rootClass = new RootClass(getGrailsDomainBinder().getMetadataBuildingContext())
        def uniqueNameGenerator = Mock(UniqueNameGenerator)
        def binder = new NaturalIdentifierBinder(uniqueNameGenerator)

        mapping.getIdentity() >> identity
        identity.getNatural() >> naturalId
        naturalId.createUniqueKey(rootClass) >> Optional.empty()

        when:
        binder.bindNaturalIdentifier(mapping, rootClass)

        then:
        0 * uniqueNameGenerator._
    }

    void "test bindNaturalIdentifier when no identity is defined"() {
        given:
        def mapping = Mock(Mapping)
        def rootClass = new RootClass(getGrailsDomainBinder().getMetadataBuildingContext())
        def uniqueNameGenerator = Mock(UniqueNameGenerator)
        def binder = new NaturalIdentifierBinder(uniqueNameGenerator)

        mapping.getIdentity() >> null

        when:
        binder.bindNaturalIdentifier(mapping, rootClass)

        then:
        0 * uniqueNameGenerator._
    }
}
