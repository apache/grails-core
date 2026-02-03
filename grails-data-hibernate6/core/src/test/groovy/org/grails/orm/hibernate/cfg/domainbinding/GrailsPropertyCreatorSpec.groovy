package org.grails.orm.hibernate.cfg.domainbinding

import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.datastore.mapping.model.PersistentProperty
import org.hibernate.boot.spi.InFlightMetadataCollector
import org.hibernate.mapping.PersistentClass
import org.hibernate.mapping.Property
import org.hibernate.mapping.Value
import spock.lang.Specification

class GrailsPropertyCreatorSpec extends HibernateGormDatastoreSpec {

    void "test createProperty method"() {
        given:
        def mockValue = Mock(Value)
        def mockPersistentClass = Mock(PersistentClass)
        def mockGrailsProperty = Mock(PersistentProperty)
        def mockMappings = null // No need to mock InFlightMetadataCollector
        def mockPropertyBinder = Mock(PropertyBinder)

        mockValue.setTypeUsingReflection(mockPersistentClass.getClassName(), mockGrailsProperty.getName()) >> { String className, String propertyName -> }
        mockValue.getTable() >> null

        def grailsPropertyCreator = new GrailsPropertyCreator(mockMappings, mockPropertyBinder)

        when:
        Property result = grailsPropertyCreator.createProperty(mockValue, mockPersistentClass, mockGrailsProperty)

        then:
        1 * mockValue.setTypeUsingReflection(mockPersistentClass.getClassName(), mockGrailsProperty.getName())
        0 * mockValue.getTable()
        1 * mockPropertyBinder.bindProperty(mockGrailsProperty, result)
        result != null
        result.getValue() == mockValue
    }
}
