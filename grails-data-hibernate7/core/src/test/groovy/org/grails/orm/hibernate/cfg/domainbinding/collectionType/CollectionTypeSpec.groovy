package org.grails.orm.hibernate.cfg.domainbinding.collectionType

import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.datastore.mapping.model.types.ToMany
import org.grails.orm.hibernate.cfg.GrailsDomainBinder
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentProperty
import org.grails.orm.hibernate.cfg.HibernateOneToManyProperty
import org.hibernate.boot.spi.InFlightMetadataCollector
import org.hibernate.mapping.Collection
import org.hibernate.mapping.PersistentClass
import spock.lang.Subject

class CollectionTypeSpec extends HibernateGormDatastoreSpec {

    // Concrete implementation for testing abstract base class
    class TestCollectionType extends CollectionType {
        TestCollectionType(GrailsDomainBinder binder) {
            super(String, binder)
        }
        @Override
        Collection create(ToMany property, PersistentClass owner, String path, InFlightMetadataCollector mappings, String sessionFactoryBeanName) {
            return null
        }
    }

    @Subject
    def collectionType

    def setup() {
        collectionType = new TestCollectionType(getGrailsDomainBinder())
    }

    def "toString should return class name"() {
        expect:
        collectionType.toString() == String.name
    }

    def "should return correct collection type for class"() {
        expect:
        collectionType.collectionTypeForClass(Set) instanceof SetCollectionType
        collectionType.collectionTypeForClass(List) instanceof ListCollectionType
        collectionType.collectionTypeForClass(java.util.Collection) instanceof BagCollectionType
        collectionType.collectionTypeForClass(Map) instanceof MapCollectionType
    }

    def "getTypeName should return type name from GrailsHibernatePersistentProperty"() {
        given:
        // Use HibernateOneToManyProperty which implements both ToMany and GrailsHibernatePersistentProperty
        def hibernateProp = Mock(HibernateOneToManyProperty)
        
        hibernateProp.getTypeName() >> "my.custom.Type"

        expect:
        collectionType.getTypeName(hibernateProp) == "my.custom.Type"
    }

    def "getTypeName should return null if not GrailsHibernatePersistentProperty"() {
        given:
        // Use a standard ToMany mock (which might fail if ToMany is considered final by Spock for some reason, 
        // but we'll try standard PersistentProperty if it fails)
        def property = Mock(org.grails.datastore.mapping.model.types.OneToMany)
        def domainClass = Mock(GrailsHibernatePersistentEntity)
        property.getOwner() >> domainClass
        domainClass.getMappedForm() >> null

        expect:
        collectionType.getTypeName(property) == null
    }
}
