package org.grails.orm.hibernate.cfg.domainbinding.secondpass

import grails.gorm.annotation.Entity
import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.cfg.domainbinding.binder.CollectionForPropertyConfigBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.CompositeIdentifierToManyToOneBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.ManyToOneBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.ManyToOneValuesBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleValueBinder
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateManyToManyProperty
import org.hibernate.mapping.Bag
import org.hibernate.mapping.ManyToOne
import org.hibernate.mapping.Table
import spock.lang.Subject

class ManyToManyElementBinderSpec extends HibernateGormDatastoreSpec {

    @Subject
    ManyToManyElementBinder binder

    void setupSpec() {
        manager.addAllDomainClasses([
            MTMEOwner,
            MTMEItem,
            MTMEBase,
            MTMESubtype,
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
        binder = new ManyToManyElementBinder(mtob, new CollectionForPropertyConfigBinder())
    }

    private HibernateManyToManyProperty propertyFor(Class ownerClass, String name = "items") {
        (getPersistentEntity(ownerClass) as GrailsHibernatePersistentEntity).getPropertyByName(name) as HibernateManyToManyProperty
    }

    def "bind sets ManyToOne element referencing the inverse owner for a standard bidirectional many-to-many"() {
        given:
        def property = propertyFor(MTMEOwner)
        def mbc = getGrailsDomainBinder().getMetadataBuildingContext()
        def collection = new Bag(mbc, null)
        collection.setCollectionTable(new Table("test", "mtme_owner_mtme_item"))

        property.setCollection(collection)

        when:
        binder.bind(property)

        then:
        collection.getElement() instanceof ManyToOne
        (collection.getElement() as ManyToOne).getReferencedEntityName() == MTMEItem.name
    }

    def "bind sets collection inverse false for a circular many-to-many"() {
        given:
        def property = propertyFor(MTMESubtype, "related")
        def mbc = getGrailsDomainBinder().getMetadataBuildingContext()
        def collection = new Bag(mbc, null)
        collection.setCollectionTable(new Table("test", "mtme_subtype_mtme_base"))

        property.setCollection(collection)

        when:
        binder.bind(property)

        then:
        property.isCircular()
        !collection.isInverse()
    }
}

@Entity
class MTMEOwner {
    Long id
    static hasMany = [items: MTMEItem]
}

@Entity
class MTMEItem {
    Long id
    String description
    static hasMany = [owners: MTMEOwner]
}

@Entity
class MTMEBase {
    Long id
    static hasMany = [subtypes: MTMESubtype]
}

@Entity
class MTMESubtype extends MTMEBase {
    static hasMany = [related: MTMEBase]
}
