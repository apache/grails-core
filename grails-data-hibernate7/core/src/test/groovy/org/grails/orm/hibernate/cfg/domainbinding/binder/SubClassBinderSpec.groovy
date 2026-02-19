package org.grails.orm.hibernate.cfg.domainbinding.binder

import grails.gorm.annotation.Entity
import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.cfg.Mapping
import org.grails.orm.hibernate.cfg.MappingCacheHolder
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.util.DefaultColumnNameFetcher
import org.grails.orm.hibernate.cfg.domainbinding.util.MultiTenantFilterBinder
import org.hibernate.boot.spi.MetadataBuildingContext
import org.hibernate.mapping.PersistentClass
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.Subclass
import spock.lang.Shared

class SubClassBinderSpec extends HibernateGormDatastoreSpec {

    SubClassBinder binder
    SubclassMappingBinder subclassMappingBinder
    MultiTenantFilterBinder multiTenantFilterBinder
    DefaultColumnNameFetcher defaultColumnNameFetcher
    MappingCacheHolder mappingCacheHolder
    MetadataBuildingContext metadataBuildingContext

    void setup() {
        def gdb = getGrailsDomainBinder()
        
        metadataBuildingContext = gdb.getMetadataBuildingContext()
        mappingCacheHolder = gdb.getMappingCacheHolder()
        subclassMappingBinder = Mock(SubclassMappingBinder)
        multiTenantFilterBinder = Mock(MultiTenantFilterBinder)
        defaultColumnNameFetcher = Mock(DefaultColumnNameFetcher)
        
        binder = new SubClassBinder(
                mappingCacheHolder,
                subclassMappingBinder,
                multiTenantFilterBinder,
                defaultColumnNameFetcher,
                "default"
        )
    }

    def "test bindSubClass with no children"() {
        given:
        def subEntity = Mock(GrailsHibernatePersistentEntity)
        subEntity.getName() >> "Child"
        subEntity.getChildEntities("default") >> []
        def rootClass = new RootClass(metadataBuildingContext)
        rootClass.setEntityName("Parent")
        rootClass.setJpaEntityName("Parent")
        def mappings = getCollector()
        def mapping = new Mapping()
        def subClass = new org.hibernate.mapping.SingleTableSubclass(rootClass, metadataBuildingContext)
        subClass.setEntityName("Child")
        subClass.setJpaEntityName("Child")

        when:
        binder.bindSubClass(subEntity, rootClass, mappings, mapping)

        then:
        1 * subclassMappingBinder.createSubclassMapping(subEntity, rootClass, mappings, mapping) >> subClass
        1 * multiTenantFilterBinder.addMultiTenantFilterIfNecessary(subEntity, subClass, mappings, defaultColumnNameFetcher)
        rootClass.getSubclasses().contains(subClass)
        mappings.getEntityBinding(subClass.getEntityName()) == subClass
    }

    def "test bindSubClass with children"() {
        given:
        def subEntity = Mock(GrailsHibernatePersistentEntity)
        def grandChildEntity = Mock(GrailsHibernatePersistentEntity)
        subEntity.getName() >> "Child"
        grandChildEntity.getName() >> "GrandChild"
        subEntity.getChildEntities("default") >> [grandChildEntity]
        grandChildEntity.getChildEntities("default") >> []

        def rootClass = new RootClass(metadataBuildingContext)
        rootClass.setEntityName("Parent")
        rootClass.setJpaEntityName("Parent")
        def mappings = getCollector()
        def mapping = new Mapping()
        
        def subClass = new org.hibernate.mapping.SingleTableSubclass(rootClass, metadataBuildingContext)
        subClass.setEntityName("Child")
        subClass.setJpaEntityName("Child")
        def grandChildSubClass = new org.hibernate.mapping.SingleTableSubclass(subClass, metadataBuildingContext)
        grandChildSubClass.setEntityName("GrandChild")
        grandChildSubClass.setJpaEntityName("GrandChild")

        when:
        binder.bindSubClass(subEntity, rootClass, mappings, mapping)

        then:
        1 * subclassMappingBinder.createSubclassMapping(subEntity, rootClass, mappings, mapping) >> subClass
        1 * subclassMappingBinder.createSubclassMapping(grandChildEntity, subClass, mappings, mapping) >> grandChildSubClass
        2 * multiTenantFilterBinder.addMultiTenantFilterIfNecessary(_, _, mappings, defaultColumnNameFetcher)
        rootClass.getSubclasses().contains(subClass)
        subClass.getSubclasses().contains(grandChildSubClass)
    }
}
