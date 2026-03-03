package org.grails.orm.hibernate.cfg.domainbinding.secondpass

import grails.gorm.annotation.Entity
import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.cfg.domainbinding.binder.CompositeIdentifierToManyToOneBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.ManyToOneValuesBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleValueBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleValueColumnBinder
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty
import org.grails.orm.hibernate.cfg.domainbinding.util.GrailsPropertyResolver
import org.hibernate.mapping.Bag
import org.hibernate.mapping.BasicValue
import org.hibernate.mapping.Column
import org.hibernate.mapping.DependantValue
import org.hibernate.mapping.Property
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.Table
import spock.lang.Subject

class CollectionKeyBinderSpec extends HibernateGormDatastoreSpec {

    @Subject
    CollectionKeyBinder binder

    void setupSpec() {
        manager.addAllDomainClasses([
            CKBBidOwner,
            CKBBidItem,
            CKBManyToManyOwner,
            CKBManyToManyItem,
            CKBUniOwner,
            CKBUniItem,
            CKBJoinKeyOwner,
            CKBJoinKeyItem,
        ])
    }

    void setup() {
        def gdb = getGrailsDomainBinder()
        def mbc = gdb.getMetadataBuildingContext()
        def ns = gdb.getNamingStrategy()
        def je = gdb.getJdbcEnvironment()
        def svb = new SimpleValueBinder(mbc, ns, je)
        def citmto = new CompositeIdentifierToManyToOneBinder(mbc, ns, je)
        def botml = new BidirectionalOneToManyLinker(new GrailsPropertyResolver())
        def dkvb = new DependentKeyValueBinder(svb, citmto)
        def svcb = new SimpleValueColumnBinder()
        binder = new CollectionKeyBinder(botml, dkvb, svcb)
    }

    private HibernateToManyProperty propertyFor(Class ownerClass, String name = "items") {
        (getPersistentEntity(ownerClass) as GrailsHibernatePersistentEntity).getPropertyByName(name) as HibernateToManyProperty
    }

    private RootClass rootClassWith(String entityName, String propName, String columnName) {
        def mbc = getGrailsDomainBinder().getMetadataBuildingContext()
        def rootClass = new RootClass(mbc)
        rootClass.setEntityName(entityName)
        def table = new Table("test", entityName.toLowerCase())
        def simpleValue = new BasicValue(mbc, table)
        simpleValue.setTypeName("long")
        simpleValue.addColumn(new Column(columnName))
        def prop = new Property()
        prop.setName(propName)
        prop.setValue(simpleValue)
        rootClass.addProperty(prop)
        return rootClass
    }

    private DependantValue keyWithTable(String tableName = "test_table") {
        def mbc = getGrailsDomainBinder().getMetadataBuildingContext()
        def table = new Table("test", tableName)
        def wrapped = new BasicValue(mbc, table)
        return new DependantValue(mbc, table, wrapped)
    }

    def "bind sets collection inverse for bidirectional one-to-many with foreign key"() {
        given:
        def property = propertyFor(CKBBidOwner)
        def associatedClass = rootClassWith(CKBBidItem.name, "owner", "OWNER_ID")
        def key = keyWithTable("ckb_bid_item")
        def collection = new Bag(getGrailsDomainBinder().getMetadataBuildingContext(), null)

        when:
        binder.bind(property, key, associatedClass, collection)

        then:
        collection.isInverse()
        key.getColumnSpan() > 0
    }

    def "bind delegates to dependentKeyValueBinder for bidirectional many-to-many"() {
        given:
        def property = propertyFor(CKBManyToManyOwner)
        def key = new DependantValue(getGrailsDomainBinder().getMetadataBuildingContext(), null, null)
        def associatedClass = new RootClass(getGrailsDomainBinder().getMetadataBuildingContext())
        def collection = new Bag(getGrailsDomainBinder().getMetadataBuildingContext(), null)

        when:
        binder.bind(property, key, associatedClass, collection)

        then:
        key.getColumnSpan() > 0
        !collection.isInverse()
    }

    def "bind uses simpleValueColumnBinder for unidirectional with join key mapping"() {
        given:
        def property = propertyFor(CKBJoinKeyOwner)
        def key = keyWithTable("ckb_join_key_owner_ckb_join_key_item")
        def associatedClass = new RootClass(getGrailsDomainBinder().getMetadataBuildingContext())
        def collection = new Bag(getGrailsDomainBinder().getMetadataBuildingContext(), null)

        when:
        binder.bind(property, key, associatedClass, collection)

        then:
        key.getTypeName() == "long"
        key.getColumnSpan() > 0
        !collection.isInverse()
    }

    def "bind delegates to dependentKeyValueBinder for unidirectional without join key mapping"() {
        given:
        def property = propertyFor(CKBUniOwner)
        def key = new DependantValue(getGrailsDomainBinder().getMetadataBuildingContext(), null, null)
        def associatedClass = new RootClass(getGrailsDomainBinder().getMetadataBuildingContext())
        def collection = new Bag(getGrailsDomainBinder().getMetadataBuildingContext(), null)

        when:
        binder.bind(property, key, associatedClass, collection)

        then:
        key.getColumnSpan() > 0
        !collection.isInverse()
    }
}

@Entity
class CKBBidOwner {
    Long id
    static hasMany = [items: CKBBidItem]
}

@Entity
class CKBBidItem {
    Long id
    CKBBidOwner owner
    static belongsTo = [owner: CKBBidOwner]
}

@Entity
class CKBManyToManyOwner {
    Long id
    static hasMany = [items: CKBManyToManyItem]
}

@Entity
class CKBManyToManyItem {
    Long id
    static hasMany = [owners: CKBManyToManyOwner]
}

@Entity
class CKBUniOwner {
    Long id
    static hasMany = [items: CKBUniItem]
}

@Entity
class CKBUniItem {
    Long id
    String description
}

@Entity
class CKBJoinKeyOwner {
    Long id
    static hasMany = [items: CKBJoinKeyItem]
    static mapping = {
        items joinTable: [key: 'owner_fk']
    }
}

@Entity
class CKBJoinKeyItem {
    Long id
    String description
}
