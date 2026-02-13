package org.grails.orm.hibernate.cfg.domainbinding

import grails.gorm.specs.HibernateGormDatastoreSpec
import grails.persistence.Entity
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.types.Association
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentProperty
import org.grails.orm.hibernate.cfg.PropertyConfig
import org.hibernate.mapping.Property
import spock.lang.Unroll
import jakarta.persistence.AccessType
import org.hibernate.mapping.Value

import org.grails.orm.hibernate.cfg.domainbinding.binder.PropertyBinder
import org.grails.orm.hibernate.cfg.domainbinding.util.CascadeBehaviorFetcher

class PropertyBinderSpec extends HibernateGormDatastoreSpec {

    abstract static class TestAssociation extends Association<PropertyConfig> implements GrailsHibernatePersistentProperty {
        TestAssociation(PersistentEntity owner, MappingContext context, java.beans.PropertyDescriptor descriptor) {
            super(owner, context, descriptor)
        }
    }

    @Unroll("test property binding for #propertyName")
    void "test property binding"() {
        given:
        def cascadeBehaviorFetcher = Mock(CascadeBehaviorFetcher)
        def binder = new PropertyBinder(cascadeBehaviorFetcher)

        def persistentProperty = Mock(GrailsHibernatePersistentProperty)
        def value = Mock(Value)
        def config = Mock(PropertyConfig)

        when:
        persistentProperty.getMappedForm() >> config
        persistentProperty.isBidirectionalManyToOneWithListMapping(_) >> (propertyName == "foos")
        config.getInsertable() >> insertable
        config.getUpdatable() >> updateable
        config.getAccessType() >> AccessType.values().find { it.name() == accessType }
        config.getLazy() >> lazy
        persistentProperty.getName() >> propertyName
        persistentProperty.isNullable() >> nullable
        value.hasAnyUpdatableColumns() >> updateable
        value.hasAnyInsertableColumns() >> insertable
        persistentProperty.isLazyAble() >> lazyAble

        and:
        def property = binder.bindProperty(persistentProperty, value)

        then:
        property.getName() == propertyName
        property.isOptional() == nullable
        property.isInsertable() == expectedInsertable
        property.isUpdateable() == expectedUpdateable
        property.getPropertyAccessorName() == expectedAccessor
        property.isLazy() == expectedLazy

        where:
        propertyName | nullable | insertable | updateable | accessType      | lazy | lazyAble | expectedInsertable | expectedUpdateable | expectedAccessor | expectedLazy
        "name"       | true     | true       | true       | "PROPERTY"      | null | false    | true               | true               | "property"       | false
        "name"       | false    | false      | false      | "FIELD"         | null | false    | false              | false              | "field"          | false
        "foos"       | true     | true       | true       | "PROPERTY"      | null | false    | false              | false              | "property"       | false
        "bar"        | true     | true       | true       | "PROPERTY"      | true | true     | true               | true               | "property"       | true
        "bar"        | true     | true       | true       | "PROPERTY"      | false| true     | true               | true               | "property"       | false
    }

    void "test cascade behavior binding"() {
        given:
        def cascadeBehaviorFetcher = Mock(CascadeBehaviorFetcher)
        def binder = new PropertyBinder(cascadeBehaviorFetcher)

        def association = Mock(TestAssociation)
        def value = Mock(Value)
        def config = Mock(PropertyConfig)

        when:
        association.getMappedForm() >> config
        config.getAccessType() >> AccessType.PROPERTY
        cascadeBehaviorFetcher.getCascadeBehaviour(association as Association) >> "all-delete-orphan"
        def property = binder.bindProperty(association as GrailsHibernatePersistentProperty, value)

        then:
        property.getCascade() == "all-delete-orphan"
    }

    void "test property accessor name with mocked persistent property"() {
        given:
        def cascadeBehaviorFetcher = Mock(CascadeBehaviorFetcher)
        def binder = new PropertyBinder(cascadeBehaviorFetcher)

        def persistentProperty = Mock(GrailsHibernatePersistentProperty)
        persistentProperty.getName() >> "name"
        def value = Mock(Value)
        def config = new PropertyConfig()
        config.setAccessType(jakarta.persistence.AccessType.PROPERTY)
        persistentProperty.getMappedForm() >> config

        when:
        def property = binder.bindProperty(persistentProperty, value)

        then:
        property.getPropertyAccessorName() == "property"
    }

}

@Entity
class PropertyBinderSpecEntity {
    String name
}
