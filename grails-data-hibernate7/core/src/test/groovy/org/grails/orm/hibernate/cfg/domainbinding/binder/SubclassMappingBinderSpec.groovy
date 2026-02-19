package org.grails.orm.hibernate.cfg.domainbinding.binder

import grails.gorm.annotation.Entity
import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.cfg.Mapping
import org.hibernate.boot.spi.MetadataBuildingContext
import org.hibernate.mapping.JoinedSubclass
import org.hibernate.mapping.PersistentClass
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.SingleTableSubclass
import org.hibernate.mapping.Subclass
import org.hibernate.mapping.UnionSubclass
import spock.lang.Shared

class SubclassMappingBinderSpec extends HibernateGormDatastoreSpec {

    SubclassMappingBinder binder
    MetadataBuildingContext metadataBuildingContext
    JoinedSubClassBinder joinedSubClassBinder
    UnionSubclassBinder unionSubclassBinder
    SingleTableSubclassBinder singleTableSubclassBinder
    ClassPropertiesBinder classPropertiesBinder

    void setup() {
        manager.addAllDomainClasses([SMBSDefaultSuperEntity, SMBSDefaultSubEntity])
        def gdb = getGrailsDomainBinder()
        metadataBuildingContext = gdb.getMetadataBuildingContext()
        joinedSubClassBinder = Mock(JoinedSubClassBinder)
        unionSubclassBinder = Mock(UnionSubclassBinder)
        singleTableSubclassBinder = Mock(SingleTableSubclassBinder)
        classPropertiesBinder = Mock(ClassPropertiesBinder)
        
        binder = new SubclassMappingBinder(
                metadataBuildingContext,
                joinedSubClassBinder,
                unionSubclassBinder,
                singleTableSubclassBinder,
                classPropertiesBinder
        )
    }

    def "test createSubclassMapping for single table inheritance"() {
        given:
        def subEntity = createPersistentEntity(SMBSDefaultSubEntity) as org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
        def rootClass = new RootClass(metadataBuildingContext)
        rootClass.setEntityName(SMBSDefaultSuperEntity.name)
        def mappings = getCollector()
        def mapping = new Mapping()
        mapping.setTablePerHierarchy(true)

        when:
        Subclass subClass = binder.createSubclassMapping(subEntity, rootClass, mappings, mapping)

        then:
        1 * singleTableSubclassBinder.bindSubClass(subEntity, _ as SingleTableSubclass, mappings)
        1 * classPropertiesBinder.bindClassProperties(subEntity, _ as Subclass, mappings)
        subClass instanceof SingleTableSubclass
        subClass.getEntityName() == SMBSDefaultSubEntity.name
    }

    def "test createSubclassMapping for joined table inheritance"() {
        given:
        def subEntity = getPersistentEntity(SMBSDefaultSubEntity) as org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
        def rootClass = new RootClass(metadataBuildingContext)
        rootClass.setEntityName(SMBSDefaultSuperEntity.name)
        def mappings = getCollector()
        def mapping = new Mapping()
        mapping.@tablePerHierarchy = false
        mapping.@tablePerConcreteClass = false

        when:
        Subclass subClass = binder.createSubclassMapping(subEntity, rootClass, mappings, mapping)

        then:
        1 * joinedSubClassBinder.bindJoinedSubClass(subEntity, _ as JoinedSubclass, mappings)
        1 * classPropertiesBinder.bindClassProperties(subEntity, _ as Subclass, mappings)
        subClass instanceof JoinedSubclass
        subClass.getEntityName() == SMBSDefaultSubEntity.name
    }

    def "test createSubclassMapping for table per concrete class inheritance"() {
        given:
        def subEntity = getPersistentEntity(SMBSDefaultSubEntity) as org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
        def rootClass = new RootClass(metadataBuildingContext)
        rootClass.setEntityName(SMBSDefaultSuperEntity.name)
        def mappings = getCollector()
        def mapping = new Mapping()
        mapping.@tablePerHierarchy = false
        mapping.@tablePerConcreteClass = true

        when:
        Subclass subClass = binder.createSubclassMapping(subEntity, rootClass, mappings, mapping)

        then:
        1 * unionSubclassBinder.bindUnionSubclass(subEntity, _ as UnionSubclass, mappings)
        1 * classPropertiesBinder.bindClassProperties(subEntity, _ as Subclass, mappings)
        subClass instanceof UnionSubclass
        subClass.getEntityName() == SMBSDefaultSubEntity.name
    }
}

@Entity
class SMBSDefaultSuperEntity {
    Long id
    String name
}

@Entity
class SMBSDefaultSubEntity extends SMBSDefaultSuperEntity {
    String subName
}
