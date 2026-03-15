package org.grails.orm.hibernate.cfg.domainbinding.secondpass

import grails.gorm.annotation.Entity
import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.cfg.domainbinding.binder.CollectionForPropertyConfigBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.CompositeIdentifierToManyToOneBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.ManyToOneBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.ManyToOneValuesBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleValueBinder
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty
import org.hibernate.mapping.Bag
import org.hibernate.mapping.ManyToOne
import org.hibernate.mapping.Table
import spock.lang.Subject

class BidirectionalMapElementBinderSpec extends HibernateGormDatastoreSpec {

    @Subject
    BidirectionalMapElementBinder binder

    void setupSpec() {
        manager.addAllDomainClasses([
            BBMEOwner,
            BBMEItem,
        ])
    }

    void setup() {
        def gdb = getGrailsDomainBinder()
        def mbc = gdb.getMetadataBuildingContext()
        def ns = gdb.getNamingStrategy()
        def je = gdb.getJdbcEnvironment()
        def svb = new SimpleValueBinder(mbc, ns, je)
        def citmto = new CompositeIdentifierToManyToOneBinder(mbc, ns, je)
        def mtob = new ManyToOneBinder(mbc, ns, svb, new ManyToOneValuesBinder(), citmto)
        binder = new BidirectionalMapElementBinder(mtob, new CollectionForPropertyConfigBinder())
    }

    private HibernateToManyProperty propertyFor(Class ownerClass, String name = "items") {
        (getPersistentEntity(ownerClass) as GrailsHibernatePersistentEntity).getPropertyByName(name) as HibernateToManyProperty
    }

    def "bind sets ManyToOne element referencing the inverse side's owner"() {
        given:
        def property = propertyFor(BBMEOwner)
        def mbc = getGrailsDomainBinder().getMetadataBuildingContext()
        def collection = new Bag(mbc, null)
        collection.setCollectionTable(new Table("test", "bbme_owner_items"))

        property.setCollection(collection)

        when:
        binder.bind(property)

        then:
        collection.getElement() instanceof ManyToOne
        (collection.getElement() as ManyToOne).getReferencedEntityName() == BBMEItem.name
    }

    def "bind honours isBidirectionalOneToManyMap on the property"() {
        given:
        def property = propertyFor(BBMEOwner)

        expect:
        property.isBidirectionalOneToManyMap()
        property.isBidirectional()
    }
}

@Entity
class BBMEOwner {
    Long id
    Map<String, BBMEItem> items
    static hasMany = [items: BBMEItem]
}

@Entity
class BBMEItem {
    Long id
    String description
    BBMEOwner owner
    static belongsTo = [owner: BBMEOwner]
}
