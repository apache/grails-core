package org.grails.orm.hibernate.cfg.domainbinding.binder

import grails.gorm.annotation.Entity
import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentEntity
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.Table
import org.hibernate.mapping.UnionSubclass

/**
 * Tests for UnionSubclassBinder using real entity classes.
 */
class UnionSubclassBinderSpec extends HibernateGormDatastoreSpec {

    UnionSubclassBinder binder
    ClassBinder classBinder = new ClassBinder()

    void setup() {
        def buildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        def namingStrategy = getGrailsDomainBinder().getNamingStrategy()
        binder = new UnionSubclassBinder(buildingContext, namingStrategy, classBinder)
    }

    void "test bind union subclass with real entities"() {
        given:
        def buildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        def mappings = buildingContext.getMetadataCollector()
        
        // Register entities in mapping context
        def rootEntity = createPersistentEntity(UnionSubClassRoot)
        def subEntity = createPersistentEntity(UnionSubClassSub)
        
        // Setup Hibernate RootClass
        def rootClass = new RootClass(buildingContext)
        rootClass.setEntityName(UnionSubClassRoot.name)
        def rootTable = new Table("US_ROOT_TABLE")
        rootTable.setName("US_ROOT_TABLE")
        rootClass.setTable(rootTable)
        
        // Setup UnionSubclass
        def unionSubclass = new UnionSubclass(rootClass, buildingContext)
        unionSubclass.setEntityName(UnionSubClassSub.name)

        when:
        binder.bindUnionSubclass(subEntity, unionSubclass, mappings)

        then:
        unionSubclass.getTable() != null
        unionSubclass.getTable().getName() != "US_ROOT_TABLE"
        unionSubclass.getClassName() == UnionSubClassSub.name
    }
}

@Entity
class UnionSubClassRoot {
    Long id
}

@Entity
class UnionSubClassSub extends UnionSubClassRoot {
    String name
    static mapping = {
        tablePerHierarchy false
        tablePerConcreteClass true
    }
}
