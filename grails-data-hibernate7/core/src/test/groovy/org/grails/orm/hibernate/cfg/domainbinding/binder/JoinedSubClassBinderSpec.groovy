package org.grails.orm.hibernate.cfg.domainbinding.binder

import grails.gorm.annotation.Entity
import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.util.ColumnNameForPropertyAndPathFetcher
import org.hibernate.boot.spi.InFlightMetadataCollector
import org.hibernate.mapping.JoinedSubclass
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.Table
import org.hibernate.mapping.SimpleValue
import org.grails.datastore.mapping.model.types.Identity

/**
 * Tests for JoinedSubClassBinder using real entity classes.
 */
class JoinedSubClassBinderSpec extends HibernateGormDatastoreSpec {

    JoinedSubClassBinder binder
    ColumnNameForPropertyAndPathFetcher fetcher
    ClassBinder classBinder = new ClassBinder()
    SimpleValueColumnBinder simpleValueColumnBinder = new SimpleValueColumnBinder()

    void setup() {
        def buildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        def namingStrategy = getGrailsDomainBinder().getNamingStrategy()
        def backticksRemover = new org.grails.orm.hibernate.cfg.domainbinding.util.BackticksRemover()
        def defaultColumnNameFetcher = new org.grails.orm.hibernate.cfg.domainbinding.util.DefaultColumnNameFetcher(namingStrategy, backticksRemover)
        
        fetcher = new org.grails.orm.hibernate.cfg.domainbinding.util.ColumnNameForPropertyAndPathFetcher(namingStrategy, defaultColumnNameFetcher, backticksRemover)
        binder = new JoinedSubClassBinder(buildingContext, namingStrategy, simpleValueColumnBinder, fetcher, classBinder)
    }

    void "test bind joined subclass with real entities"() {
        given:
        def buildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        def mappings = buildingContext.getMetadataCollector()
        
        // Register entities in mapping context
        def rootEntity = createPersistentEntity(JoinedSubClassRoot)
        def subEntity = createPersistentEntity(JoinedSubClassSub)
        
        // Setup Hibernate RootClass
        def rootClass = new RootClass(buildingContext)
        rootClass.setEntityName(JoinedSubClassRoot.name)
        def rootTable = new Table("JS_ROOT_TABLE")
        rootTable.setName("JS_ROOT_TABLE")
        rootClass.setTable(rootTable)
        
        def idProperty = new org.hibernate.mapping.Property()
        idProperty.setName("id")
        def idValue = new org.hibernate.mapping.BasicValue(buildingContext, rootTable)
        idValue.setTypeName("long")
        idProperty.setValue(idValue)
        rootClass.setIdentifier(idValue)
        rootClass.setIdentifierProperty(idProperty)
        rootClass.createPrimaryKey()
        
        // The JoinedSubclass needs the parent PersistentClass
        def joinedSubclass = new JoinedSubclass(rootClass, buildingContext)
        joinedSubclass.setEntityName(JoinedSubClassSub.name)

        when:
        binder.bindJoinedSubClass(subEntity, joinedSubclass, mappings)

        then:
        joinedSubclass.getTable() != null
        joinedSubclass.getTable().getName() != "JS_ROOT_TABLE"
        joinedSubclass.getKey() != null
        joinedSubclass.getKey().getColumnSpan() > 0
        joinedSubclass.getTable().getPrimaryKey() != null
    }
}

@Entity
class JoinedSubClassRoot {
    Long id
}

@Entity
class JoinedSubClassSub extends JoinedSubClassRoot {
    String name
    static mapping = {
        tablePerHierarchy false
    }
}
