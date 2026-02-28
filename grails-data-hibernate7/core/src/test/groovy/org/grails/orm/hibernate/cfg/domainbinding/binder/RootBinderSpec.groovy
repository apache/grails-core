package org.grails.orm.hibernate.cfg.domainbinding.binder


import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.cfg.Mapping
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.util.MultiTenantFilterBinder
import org.hibernate.boot.spi.MetadataBuildingContext
import org.hibernate.mapping.RootClass

class RootBinderSpec extends HibernateGormDatastoreSpec {

    RootBinder binder
    MultiTenantFilterBinder multiTenantFilterBinder
    SubClassBinder subClassBinder
    RootPersistentClassCommonValuesBinder rootPersistentClassCommonValuesBinder
    DiscriminatorPropertyBinder discriminatorPropertyBinder
    MetadataBuildingContext metadataBuildingContext
    PersistentEntityNamingStrategy namingStrategy

    void setup() {
        def gdb = getGrailsDomainBinder()
        metadataBuildingContext = gdb.getMetadataBuildingContext()
        namingStrategy = gdb.getNamingStrategy()
        
        multiTenantFilterBinder = Mock(MultiTenantFilterBinder)
        subClassBinder = Mock(SubClassBinder)
        rootPersistentClassCommonValuesBinder = Mock(RootPersistentClassCommonValuesBinder)
        discriminatorPropertyBinder = Mock(DiscriminatorPropertyBinder)
        
        binder = new RootBinder(
                "default",
                multiTenantFilterBinder,
                subClassBinder,
                rootPersistentClassCommonValuesBinder,
                discriminatorPropertyBinder
        )
    }

    def "test bindRoot with no children"() {
        given:
        def entity = Mock(GrailsHibernatePersistentEntity)
        entity.getName() >> "Parent"
        entity.getChildEntities("default") >> []
        entity.getMappedForm() >> new Mapping()
        
        def mappings = getCollector()
        def rootClass = new RootClass(metadataBuildingContext)
        rootClass.setEntityName("Parent")
        rootClass.setJpaEntityName("Parent")

        when:
        binder.bindRoot(entity, mappings)

        then:
        1 * rootPersistentClassCommonValuesBinder.bindRootPersistentClassCommonValues(entity, [], mappings) >> rootClass
        0 * discriminatorPropertyBinder.bindDiscriminatorProperty(_)
        0 * subClassBinder.bindSubClass(_, _, _, _)
        1 * multiTenantFilterBinder.addMultiTenantFilterIfNecessary(entity, rootClass)
        mappings.getEntityBinding("Parent") == rootClass
    }

    def "test bindRoot with children and table-per-hierarchy"() {
        given:
        def entity = Mock(GrailsHibernatePersistentEntity)
        def childEntity = Mock(GrailsHibernatePersistentEntity)
        entity.getName() >> "Parent"
        entity.getChildEntities("default") >> [childEntity]
        def mapping = new Mapping()
        mapping.setTablePerHierarchy(true)
        entity.getMappedForm() >> mapping
        entity.isTablePerHierarchy() >> true
        
        def mappings = getCollector()
        def rootClass = new RootClass(metadataBuildingContext)
        rootClass.setEntityName("Parent")
        rootClass.setJpaEntityName("Parent")

        when:
        binder.bindRoot(entity, mappings)

        then:
        1 * rootPersistentClassCommonValuesBinder.bindRootPersistentClassCommonValues(entity, [childEntity], mappings) >> rootClass
        1 * discriminatorPropertyBinder.bindDiscriminatorProperty(rootClass)
        1 * subClassBinder.bindSubClass(childEntity, rootClass, mappings, mapping)
        1 * multiTenantFilterBinder.addMultiTenantFilterIfNecessary(entity, rootClass)
        mappings.getEntityBinding("Parent") == rootClass
    }

    def "test bindRoot already mapped"() {
        given:
        def entity = Mock(GrailsHibernatePersistentEntity)
        entity.getName() >> "Parent"
        def mappings = getCollector()
        def rootClass = new RootClass(metadataBuildingContext)
        rootClass.setEntityName("Parent")
        rootClass.setJpaEntityName("Parent")
        mappings.addEntityBinding(rootClass)

        when:
        binder.bindRoot(entity, mappings)

        then:
        0 * rootPersistentClassCommonValuesBinder.bindRootPersistentClassCommonValues(_, _, _)
    }
}
