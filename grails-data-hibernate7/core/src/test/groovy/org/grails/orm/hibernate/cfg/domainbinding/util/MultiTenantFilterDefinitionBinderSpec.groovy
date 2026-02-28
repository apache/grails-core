package org.grails.orm.hibernate.cfg.domainbinding.util

import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.datastore.mapping.model.config.GormProperties
import org.hibernate.boot.spi.InFlightMetadataCollector
import org.hibernate.mapping.BasicValue
import org.hibernate.mapping.Property
import org.hibernate.mapping.Table
import org.hibernate.engine.spi.FilterDefinition
import org.hibernate.metamodel.mapping.JdbcMapping

/**
 * Tests for MultiTenantFilterDefinitionBinder.
 */
class MultiTenantFilterDefinitionBinderSpec extends HibernateGormDatastoreSpec {

    MultiTenantFilterDefinitionBinder filterDefinitionBinder = new MultiTenantFilterDefinitionBinder()
    InFlightMetadataCollector mockCollector = GroovyMock(InFlightMetadataCollector)

    void "test ensureGlobalFilterDefinition adds filter definition if not present"() {
        given:
        def buildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        def property = new Property()
        property.setName("tenantId")
        
        def table = new Table("ROOT_TABLE")
        def value = new BasicValue(buildingContext, table)
        value.setTypeName("long")
        property.setValue(value)
        
        def filterName = GormProperties.TENANT_IDENTITY

        when:
        filterDefinitionBinder.ensureGlobalFilterDefinition(mockCollector, filterName, property)

        then:
        1 * mockCollector.getFilterDefinition(filterName) >> null
        1 * mockCollector.addFilterDefinition({ FilterDefinition fd -> 
            fd.getFilterName() == filterName && 
            fd.getDefaultFilterCondition() == null &&
            fd.getParameterNames().contains(filterName)
        })
    }

    void "test ensureGlobalFilterDefinition does not add if already present"() {
        given:
        def property = new Property()
        def filterName = GormProperties.TENANT_IDENTITY
        def existingFilter = Mock(FilterDefinition)

        when:
        filterDefinitionBinder.ensureGlobalFilterDefinition(mockCollector, filterName, property)

        then:
        1 * mockCollector.getFilterDefinition(filterName) >> existingFilter
        0 * mockCollector.addFilterDefinition(_)
    }

    void "test ensureGlobalFilterDefinition does not add if property value is not BasicValue"() {
        given:
        def property = new Property()
        def filterName = GormProperties.TENANT_IDENTITY
        
        // Property with no value (null)
        property.setValue(null)

        when:
        filterDefinitionBinder.ensureGlobalFilterDefinition(mockCollector, filterName, property)

        then:
        1 * mockCollector.getFilterDefinition(filterName) >> null
        0 * mockCollector.addFilterDefinition(_)
    }
}
