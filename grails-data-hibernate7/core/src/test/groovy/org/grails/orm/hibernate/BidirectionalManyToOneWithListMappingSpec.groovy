package org.grails.orm.hibernate

import org.hibernate.boot.Metadata
import org.hibernate.mapping.PersistentClass

import grails.gorm.annotation.Entity
import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.orm.hibernate.cfg.domainbinding.BidirectionalManyToOneWithListMapping
import org.hibernate.mapping.ManyToOne
import org.hibernate.mapping.OneToMany
import org.hibernate.mapping.OneToOne
import org.hibernate.mapping.Property

class BidirectionalManyToOneWithListMappingSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.addAllDomainClasses([Spec4_OneToOneParent])
    }

    void "test that it is not an association property"() {

        given:
        def belongsToClass = createPersistentEntity(NonAssociationEntity, grailsDomainBinder)
        def grailsProperty = belongsToClass.getPropertyByName("name")
        def hibernateProperty = null;
        def mapping = new BidirectionalManyToOneWithListMapping()

        when:
        boolean isBidirectional = mapping.isBidirectionalManyToOneWithListMapping(grailsProperty, hibernateProperty)

        then:
        !isBidirectional
    }

    void "test that the owning many-to-one side is not the target mapping"() {

        given:
        def belongsToClass = createPersistentEntity(Spec1_BidirListChild, grailsDomainBinder)
        PersistentProperty grailsProperty = belongsToClass.getPropertyByName("parent")
        def hibernateProperty = new Property()

        def manyToOne = new ManyToOne(getGrailsDomainBinder().getMetadataBuildingContext(),null)
        hibernateProperty.setValue(manyToOne)
        def mapping = new BidirectionalManyToOneWithListMapping()

        when:
        boolean isBidirectional = mapping.isBidirectionalManyToOneWithListMapping(grailsProperty, hibernateProperty)

        then:
        !isBidirectional
    }



    void "test that a unidirectional one-to-many is not the target mapping"() {
        given:
        def parentClass = createPersistentEntity(Spec2_UnidirParent, grailsDomainBinder)
        PersistentProperty grailsProperty = parentClass.getPropertyByName("children")
        def hibernateProperty = new Property()
        hibernateProperty.setValue(Mock(OneToMany))
        def mapping = new BidirectionalManyToOneWithListMapping()

        when:
        boolean isBidirectional = mapping.isBidirectionalManyToOneWithListMapping(grailsProperty, hibernateProperty)

        then:
        !isBidirectional
    }

    void "test that a bidirectional one-to-many with a set is not the target mapping"() {
        given:
        def parentClass = createPersistentEntity(Spec3_BidirSetParent, grailsDomainBinder)
        PersistentProperty grailsProperty = parentClass.getPropertyByName("children")
        def hibernateProperty = new Property()
        hibernateProperty.setValue(Mock(OneToMany))
        def mapping = new BidirectionalManyToOneWithListMapping()

        when:
        boolean isBidirectional = mapping.isBidirectionalManyToOneWithListMapping(grailsProperty, hibernateProperty)

        then:
        !isBidirectional
    }

    void "test that a one-to-one association is not the target mapping"() {
        given:
        def parentClass = createPersistentEntity(Spec4_OneToOneParent, grailsDomainBinder)
        PersistentProperty grailsProperty = parentClass.getPropertyByName("child")
        def hibernateProperty = new Property()

        def metadata = manager.hibernateDatastore.getMetadata()
        def rootClass = metadata.getEntityBinding(Spec4_OneToOneParent.name)


        def one = new OneToOne(getGrailsDomainBinder().getMetadataBuildingContext(), null, rootClass)
        hibernateProperty.setValue(one)
        def mapping = new BidirectionalManyToOneWithListMapping()

        when:
        boolean isBidirectional = mapping.isBidirectionalManyToOneWithListMapping(grailsProperty, hibernateProperty)

        then:
        !isBidirectional
    }

    void "test that an embedded property is not the target mapping"() {
        given:
        def parentClass = createPersistentEntity(Spec5_EmbeddedParent, grailsDomainBinder)
        PersistentProperty grailsProperty = parentClass.getPropertyByName("child")
        def hibernateProperty = null
        def mapping = new BidirectionalManyToOneWithListMapping()

        when:
        boolean isBidirectional = mapping.isBidirectionalManyToOneWithListMapping(grailsProperty, hibernateProperty)

        then:
        !isBidirectional
    }

}

@Entity
class NonAssociationEntity {
    String name
}

@Entity
class Spec1_BidirListParent {
    static hasMany = [children: Spec1_BidirListChild]
    static mappedBy = [children: 'parent']
}

@Entity
class Spec1_BidirListChild {
    static belongsTo = [parent: Spec1_BidirListParent]
}

@Entity
class Spec2_UnidirParent {
    static hasMany = [children: Spec2_UnidirChild]
}

@Entity
class Spec2_UnidirChild {
    String name
}

@Entity
class Spec3_BidirSetParent {
    Set<Spec3_BidirSetChild> children
    static hasMany = [children: Spec3_BidirSetChild]
    static mappedBy = [children: 'parent']
}

@Entity
class Spec3_BidirSetChild {
    static belongsTo = [parent: Spec3_BidirSetParent]
}
@Entity
class Spec4_OneToOneParent {
    Spec4_OneToOneChild child
}

@Entity
class Spec4_OneToOneChild {
    static belongsTo = [parent: Spec4_OneToOneParent]
}
@Entity
class Spec5_EmbeddedParent {
    Spec5_EmbeddedChild child
    static embedded = ['child']
}
@Entity
class Spec5_EmbeddedChild {
    String name
}
