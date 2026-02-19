package org.grails.orm.hibernate.cfg.domainbinding.util

import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.datastore.mapping.model.config.GormProperties
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.hibernate.boot.spi.InFlightMetadataCollector
import org.hibernate.mapping.BasicValue
import org.hibernate.mapping.Property
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.SingleTableSubclass
import org.hibernate.mapping.JoinedSubclass
import org.hibernate.mapping.UnionSubclass
import org.hibernate.mapping.Table
import org.hibernate.engine.spi.FilterDefinition
import org.grails.datastore.mapping.model.types.TenantId

/**
 * Tests for MultiTenantFilterBinder.
 */
class MultiTenantFilterBinderSpec extends HibernateGormDatastoreSpec {

    GrailsPropertyResolver grailsPropertyResolver = Mock(GrailsPropertyResolver)
    MultiTenantFilterBinder filterBinder = new MultiTenantFilterBinder(grailsPropertyResolver)
    DefaultColumnNameFetcher fetcher = Mock(DefaultColumnNameFetcher)
    InFlightMetadataCollector mockCollector = GroovyMock(InFlightMetadataCollector)

    void "test add multi tenant filter to root class"() {
        given:
        def entity = Mock(GrailsHibernatePersistentEntity)
        def buildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        def persistentClass = new RootClass(buildingContext)
        
        def tenantId = Mock(TenantId)
        def property = new Property()
        property.setName("tenantId")
        
        def table = new Table("ROOT_TABLE")
        def value = new BasicValue(buildingContext, table)
        value.setTypeName("long")
        
        entity.isMultiTenant() >> true
        entity.getTenantId() >> tenantId
        tenantId.getName() >> "tenantId"
        grailsPropertyResolver.getProperty(persistentClass, "tenantId") >> property
        
        property.setValue(value)
        persistentClass.setTable(table)
        persistentClass.addProperty(property)
        
        // Setup for FilterDefinition
        mockCollector.getFilterDefinition(GormProperties.TENANT_IDENTITY) >> null
        
        entity.getMultiTenantFilterCondition(fetcher) >> "tenant_id = :tenantId"

        when:
        filterBinder.addMultiTenantFilterIfNecessary(entity, persistentClass, mockCollector, fetcher)

        then:
        1 * mockCollector.addFilterDefinition(_ as FilterDefinition)
        persistentClass.getFilters().any { it.getName() == GormProperties.TENANT_IDENTITY && it.getCondition() == "tenant_id = :tenantId" }
    }

    void "test skip filter for single table subclass (redundant)"() {
        given:
        def entity = Mock(GrailsHibernatePersistentEntity)
        def buildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        def rootClass = new RootClass(buildingContext)
        def table = new Table("ROOT_TABLE")
        rootClass.setTable(table)
        
        def persistentClass = new SingleTableSubclass(rootClass, buildingContext)
        def tenantId = Mock(TenantId)
        
        def property = new Property()
        property.setName("tenantId")
        def value = new BasicValue(buildingContext, table)
        value.setTypeName("long")
        property.setValue(value)
        
        rootClass.addProperty(property)
        
        entity.isMultiTenant() >> true
        entity.getTenantId() >> tenantId
        tenantId.getName() >> "tenantId"
        grailsPropertyResolver.getProperty(persistentClass, "tenantId") >> property // Add stub here
        
        entity.isTablePerHierarchySubclass() >> true
        mockCollector.getFilterDefinition(_) >> Mock(FilterDefinition)

        when:
        filterBinder.addMultiTenantFilterIfNecessary(entity, persistentClass, mockCollector, fetcher)

        then:
        !persistentClass.getFilters().any { it.getName() == GormProperties.TENANT_IDENTITY }
    }

    void "test skip filter for joined subclass if inherited (alias safety)"() {
        given:
        def entity = Mock(GrailsHibernatePersistentEntity)
        def buildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        def rootClass = new RootClass(buildingContext)
        def rootTable = new Table("ROOT_TABLE")
        rootTable.setName("ROOT_TABLE")
        rootClass.setTable(rootTable)

        def persistentClass = new JoinedSubclass(rootClass, buildingContext)
        def subTable = new Table("SUB_TABLE")
        subTable.setName("SUB_TABLE")
        persistentClass.setTable(subTable)
        
        def tenantId = Mock(TenantId)
        def property = new Property()
        property.setName("tenantId")
        def value = new BasicValue(buildingContext, rootTable)
        value.setTypeName("long")
        property.setValue(value)
        
        rootClass.addProperty(property)

        entity.isMultiTenant() >> true
        entity.getTenantId() >> tenantId
        tenantId.getName() >> "tenantId"
        grailsPropertyResolver.getProperty(persistentClass, "tenantId") >> property // Add stub here
        
        mockCollector.getFilterDefinition(_) >> Mock(FilterDefinition)

        when:
        filterBinder.addMultiTenantFilterIfNecessary(entity, persistentClass, mockCollector, fetcher)

        then:
        !persistentClass.getFilters().any { it.getName() == GormProperties.TENANT_IDENTITY }
    }

    void "test add filter for union subclass (own table)"() {
        given:
        def entity = Mock(GrailsHibernatePersistentEntity)
        def buildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        def rootClass = new RootClass(buildingContext)
        def subTable = new Table("SUB_TABLE")

        def persistentClass = new UnionSubclass(rootClass, buildingContext)
        persistentClass.setTable(subTable)
        
        def tenantId = Mock(TenantId)
        def property = new Property()
        property.setName("tenantId")
        def value = new BasicValue(buildingContext, subTable)
        value.setTypeName("long")
        property.setValue(value)
        
        persistentClass.addProperty(property)

        entity.isMultiTenant() >> true
        entity.getTenantId() >> tenantId
        tenantId.getName() >> "tenantId"
        grailsPropertyResolver.getProperty(persistentClass, "tenantId") >> property // Add stub here
        
        entity.isTablePerHierarchySubclass() >> false
        mockCollector.getFilterDefinition(_) >> Mock(FilterDefinition)
        entity.getMultiTenantFilterCondition(fetcher) >> "tenant_id = :tenantId"

        when:
        filterBinder.addMultiTenantFilterIfNecessary(entity, persistentClass, mockCollector, fetcher)

        then:
        persistentClass.getFilters().any { it.getName() == GormProperties.TENANT_IDENTITY && it.getCondition() == "tenant_id = :tenantId" }
    }
}
