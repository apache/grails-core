package org.grails.orm.hibernate.cfg.domainbinding.secondpass

import grails.gorm.specs.HibernateGormDatastoreSpec
import org.hibernate.boot.spi.MetadataBuildingContext
import org.hibernate.mapping.Bag
import org.hibernate.mapping.Collection
import org.hibernate.mapping.DependantValue
import org.hibernate.mapping.KeyValue
import org.hibernate.mapping.PersistentClass
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.Table
import org.hibernate.mapping.SimpleValue
import org.hibernate.mapping.Property
import org.hibernate.mapping.BasicValue
import spock.lang.Subject

class PrimaryKeyValueCreatorSpec extends HibernateGormDatastoreSpec {

    @Subject
    PrimaryKeyValueCreator creator

    MetadataBuildingContext metadataBuildingContext

    def setup() {
        metadataBuildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        creator = new PrimaryKeyValueCreator(metadataBuildingContext)
    }

    void "test createPrimaryKeyValue with default identifier"() {
        given:
        Table ownerTable = new Table()
        ownerTable.setName("OWNER")
        RootClass owner = new RootClass(metadataBuildingContext)
        owner.setTable(ownerTable)
        
        KeyValue identifier = new BasicValue(metadataBuildingContext, ownerTable)
        owner.setIdentifier(identifier)

        Table collectionTable = new Table()
        collectionTable.setName("COLLECTION")
        Collection collection = new Bag(metadataBuildingContext, owner)
        collection.setCollectionTable(collectionTable)
        collection.setSorted(true)

        when:
        DependantValue result = creator.createPrimaryKeyValue(collection)

        then:
        result != null
        result.getTable().name == "COLLECTION"
        result.isSorted()
        result.isNullable()
        result.isUpdateable()
    }

    void "test createPrimaryKeyValue with referenced property"() {
        given:
        Table ownerTable = new Table()
        ownerTable.setName("OWNER")
        RootClass owner = new RootClass(metadataBuildingContext)
        owner.setTable(ownerTable)
        
        Property referencedProperty = new Property()
        referencedProperty.name = "myProp"
        KeyValue propertyValue = new BasicValue(metadataBuildingContext, ownerTable)
        referencedProperty.setValue(propertyValue)
        owner.addProperty(referencedProperty)

        Table collectionTable = new Table()
        collectionTable.setName("COLLECTION")
        Collection collection = new Bag(metadataBuildingContext, owner)
        collection.setCollectionTable(collectionTable)
        collection.setReferencedPropertyName("myProp")
        collection.setSorted(false)

        when:
        DependantValue result = creator.createPrimaryKeyValue(collection)

        then:
        result != null
        !result.isSorted()
    }
}
