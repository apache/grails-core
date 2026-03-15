package org.grails.orm.hibernate.cfg.domainbinding.secondpass

import grails.gorm.annotation.Entity
import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.datastore.mapping.model.DatastoreConfigurationException
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty
import org.hibernate.mapping.Bag
import org.hibernate.mapping.BasicValue
import org.hibernate.mapping.Column
import org.hibernate.mapping.Property
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.Table
import spock.lang.Subject

class CollectionOrderByBinderSpec extends HibernateGormDatastoreSpec {

    @Subject
    CollectionOrderByBinder binder = new CollectionOrderByBinder()

    void setupSpec() {
        manager.addAllDomainClasses([
            COBOwnerEntity,
            COBAssociatedItem,
            COBUnidirectionalOwner,
            COBBaseItem,
            COBSubItem,
            COBHierarchyOwner,
        ])
    }

    private HibernateToManyProperty propertyFor(Class ownerClass, String name = "items") {
        (getPersistentEntity(ownerClass) as GrailsHibernatePersistentEntity).getPropertyByName(name) as HibernateToManyProperty
    }

    private RootClass rootClassWith(String entityName, String propertyName, String columnName) {
        def mbc = getGrailsDomainBinder().getMetadataBuildingContext()
        def rootClass = new RootClass(mbc)
        rootClass.setEntityName(entityName)
        def table = new Table("test", entityName.toLowerCase())
        def simpleValue = new BasicValue(mbc, table)
        simpleValue.setTypeName("string")
        simpleValue.addColumn(new Column(columnName))
        def prop = new Property()
        prop.setName(propertyName)
        prop.setValue(simpleValue)
        rootClass.addProperty(prop)
        return rootClass
    }

    def "bind sets orderBy when sort is configured on a bidirectional association"() {
        given:
        def property = propertyFor(COBOwnerEntity)
        def collection = new Bag(getGrailsDomainBinder().getMetadataBuildingContext(), null)
        collection.setRole("${COBOwnerEntity.name}.items")
        def associatedClass = rootClassWith(COBAssociatedItem.name, "value", "VALUE")
        property.getMappedForm().setSort("value")
        property.getMappedForm().setOrder("desc")

        property.setCollection(collection)

        when:
        binder.bind(property, associatedClass)

        then:
        collection.getOrderBy() != null
        collection.getOrderBy().contains("desc")
    }

    def "bind defaults to asc when order is not specified"() {
        given:
        def property = propertyFor(COBOwnerEntity)
        def collection = new Bag(getGrailsDomainBinder().getMetadataBuildingContext(), null)
        collection.setRole("${COBOwnerEntity.name}.items")
        def associatedClass = rootClassWith(COBAssociatedItem.name, "value", "VALUE")
        property.getMappedForm().setSort("value")

        property.setCollection(collection)

        when:
        binder.bind(property, associatedClass)

        then:
        collection.getOrderBy() != null
        collection.getOrderBy().contains("asc")
    }

    def "bind throws DatastoreConfigurationException for unidirectional one-to-many with sort"() {
        given:
        def property = propertyFor(COBUnidirectionalOwner)
        def collection = new Bag(getGrailsDomainBinder().getMetadataBuildingContext(), null)
        def associatedClass = new RootClass(getGrailsDomainBinder().getMetadataBuildingContext())
        property.getMappedForm().setSort("value")

        property.setCollection(collection)

        when:
        binder.bind(property, associatedClass)

        then:
        thrown(DatastoreConfigurationException)
    }

    def "bind does not set orderBy when no sort is configured"() {
        given:
        def property = propertyFor(COBOwnerEntity)
        def collection = new Bag(getGrailsDomainBinder().getMetadataBuildingContext(), null)
        def associatedClass = new RootClass(getGrailsDomainBinder().getMetadataBuildingContext())

        property.setCollection(collection)

        when:
        binder.bind(property, associatedClass)

        then:
        collection.getOrderBy() == null
    }

    def "bind sets where clause for table-per-hierarchy subclass"() {
        given:
        def property = propertyFor(COBHierarchyOwner)
        def collection = new Bag(getGrailsDomainBinder().getMetadataBuildingContext(), null)
        def associatedClass = new RootClass(getGrailsDomainBinder().getMetadataBuildingContext())

        property.setCollection(collection)

        when:
        binder.bind(property, associatedClass)

        then:
        collection.getWhere() != null
        collection.getWhere().contains("DTYPE in (")
        collection.getWhere().contains("COBSubItem")
    }
}

@Entity
class COBOwnerEntity {
    Long id
    static hasMany = [items: COBAssociatedItem]
}

@Entity
class COBAssociatedItem {
    Long id
    String value
    COBOwnerEntity owner
    static belongsTo = [owner: COBOwnerEntity]
}

@Entity
class COBUnidirectionalOwner {
    Long id
    static hasMany = [items: COBAssociatedItem]
}

@Entity
class COBBaseItem {
    Long id
    String value
}

@Entity
class COBSubItem extends COBBaseItem {
}

@Entity
class COBHierarchyOwner {
    Long id
    static hasMany = [items: COBSubItem]
}
