package org.grails.orm.hibernate.cfg.domainbinding.secondpass

import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.cfg.CompositeIdentity
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentProperty
import org.grails.orm.hibernate.cfg.Mapping
import org.grails.orm.hibernate.cfg.domainbinding.binder.CompositeIdentifierToManyToOneBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleValueBinder
import org.hibernate.mapping.DependantValue
import spock.lang.Subject

import static org.grails.orm.hibernate.cfg.GrailsDomainBinder.EMPTY_PATH

class DependentKeyValueBinderSpec extends HibernateGormDatastoreSpec {

    SimpleValueBinder simpleValueBinder = Mock(SimpleValueBinder)
    CompositeIdentifierToManyToOneBinder compositeIdentifierToManyToOneBinder = Mock(CompositeIdentifierToManyToOneBinder)

    @Subject
    DependentKeyValueBinder binder = new DependentKeyValueBinder(simpleValueBinder, compositeIdentifierToManyToOneBinder)

    void "test bind without composite identifier"() {
        given:
        GrailsHibernatePersistentProperty property = Mock(GrailsHibernatePersistentProperty)
        GrailsHibernatePersistentEntity owner = Mock(GrailsHibernatePersistentEntity)
        Mapping mapping = Mock(Mapping)
        DependantValue key = Mock(DependantValue)

        property.getHibernateOwner() >> owner
        owner.getMappedForm() >> mapping
        mapping.hasCompositeIdentifier() >> false

        when:
        binder.bind(property, key)

        then:
        1 * simpleValueBinder.bindSimpleValue(property, null, key, EMPTY_PATH)
        0 * compositeIdentifierToManyToOneBinder.bindCompositeIdentifierToManyToOne(*_)
    }

    void "test bind with composite identifier and join column support"() {
        given:
        GrailsHibernatePersistentProperty property = Mock(GrailsHibernatePersistentProperty)
        GrailsHibernatePersistentEntity owner = Mock(GrailsHibernatePersistentEntity)
        Mapping mapping = Mock(Mapping)
        CompositeIdentity ci = new CompositeIdentity()
        DependantValue key = Mock(DependantValue)

        property.getHibernateOwner() >> owner
        owner.getMappedForm() >> mapping
        mapping.hasCompositeIdentifier() >> true
        mapping.getIdentity() >> ci
        property.supportsJoinColumnMapping() >> true

        when:
        binder.bind(property, key)

        then:
        1 * compositeIdentifierToManyToOneBinder.bindCompositeIdentifierToManyToOne(property, key, ci, owner, EMPTY_PATH)
        0 * simpleValueBinder.bindSimpleValue(*_)
    }

    void "test bind with composite identifier but NO join column support"() {
        given:
        GrailsHibernatePersistentProperty property = Mock(GrailsHibernatePersistentProperty)
        GrailsHibernatePersistentEntity owner = Mock(GrailsHibernatePersistentEntity)
        Mapping mapping = Mock(Mapping)
        DependantValue key = Mock(DependantValue)

        property.getHibernateOwner() >> owner
        owner.getMappedForm() >> mapping
        mapping.hasCompositeIdentifier() >> true
        property.supportsJoinColumnMapping() >> false

        when:
        binder.bind(property, key)

        then:
        1 * simpleValueBinder.bindSimpleValue(property, null, key, EMPTY_PATH)
        0 * compositeIdentifierToManyToOneBinder.bindCompositeIdentifierToManyToOne(*_)
    }
}
