package org.grails.orm.hibernate.cfg.domainbinding.util

import grails.gorm.specs.HibernateGormDatastoreSpec
import org.hibernate.MappingException
import org.hibernate.mapping.Component
import org.hibernate.mapping.PersistentClass
import org.hibernate.mapping.Property
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.Table
import org.hibernate.mapping.BasicValue
import spock.lang.Subject

class GrailsPropertyResolverSpec extends HibernateGormDatastoreSpec {

    @Subject
    GrailsPropertyResolver resolver = new GrailsPropertyResolver()

    void "should retrieve property directly from PersistentClass"() {
        given:
        RootClass rootClass = new RootClass(getGrailsDomainBinder().getMetadataBuildingContext())
        rootClass.setEntityName("TestEntity")
        
        Property property = new Property()
        property.setName("testProperty")
        rootClass.addProperty(property)

        when:
        Property result = resolver.getProperty(rootClass, "testProperty")

        then:
        result == property
    }

    void "should retrieve property from composite key if not found directly"() {
        given:
        RootClass rootClass = new RootClass(getGrailsDomainBinder().getMetadataBuildingContext())
        rootClass.setEntityName("TestCompositeEntity")
        
        Table table = new Table("test_table")
        Component compositeKey = new Component(getGrailsDomainBinder().getMetadataBuildingContext(), table, rootClass)
        
        Property keyProperty = new Property()
        keyProperty.setName("keyPart")
        compositeKey.addProperty(keyProperty)
        
        rootClass.setIdentifier(compositeKey)

        when:
        Property result = resolver.getProperty(rootClass, "keyPart")

        then:
        result == keyProperty
    }

    void "should throw MappingException if property not found and no composite key fallback"() {
        given:
        RootClass rootClass = new RootClass(getGrailsDomainBinder().getMetadataBuildingContext())
        rootClass.setEntityName("TestEntity")

        when:
        resolver.getProperty(rootClass, "nonExistent")

        then:
        thrown(MappingException)
    }

    void "should throw MappingException if property not found and composite key does not contain it"() {
        given:
        RootClass rootClass = new RootClass(getGrailsDomainBinder().getMetadataBuildingContext())
        rootClass.setEntityName("TestCompositeEntity")

        Table table = new Table("test_table")
        Component compositeKey = new Component(getGrailsDomainBinder().getMetadataBuildingContext(), table, rootClass)
        rootClass.setIdentifier(compositeKey)

        when:
        resolver.getProperty(rootClass, "nonExistent")

        then:
        thrown(MappingException)
    }
}
