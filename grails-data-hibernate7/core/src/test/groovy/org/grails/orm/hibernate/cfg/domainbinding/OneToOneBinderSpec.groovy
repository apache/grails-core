package org.grails.orm.hibernate.cfg.domainbinding

import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.datastore.mapping.model.types.OneToOne as GormOneToOne
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentProperty
import org.grails.orm.hibernate.cfg.PropertyConfig
import org.hibernate.FetchMode
import org.hibernate.mapping.OneToOne as HibernateOneToOne
import org.hibernate.mapping.RootClass
import org.hibernate.type.ForeignKeyDirection
import spock.lang.Subject

import org.grails.orm.hibernate.cfg.domainbinding.binder.OneToOneBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleValueBinder

class OneToOneBinderSpec extends HibernateGormDatastoreSpec {

    @Subject
    OneToOneBinder binder

    SimpleValueBinder mockSimpleValueBinder = Mock(SimpleValueBinder)

    def setup() {
        binder = new OneToOneBinder(getGrailsDomainBinder().getMetadataBuildingContext(), getGrailsDomainBinder().getNamingStrategy(), mockSimpleValueBinder)
    }

    def "should bind one-to-one mapping with defaults"() {
        given:
        def metadataBuildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        def ownerRoot = new RootClass(metadataBuildingContext)
        
        def gormOneToOne = Mock(GormOneToOne, additionalInterfaces: [GrailsHibernatePersistentProperty])
        def otherSide = Mock(GormOneToOne)
        def owner = Mock(PersistentEntity)
        def otherOwner = Mock(PersistentEntity)

        gormOneToOne.getInverseSide() >> otherSide
        gormOneToOne.getName() >> "myOneToOne"
        gormOneToOne.getOwner() >> owner
        
        otherSide.isHasOne() >> false
        otherSide.getOwner() >> otherOwner
        otherSide.getName() >> "otherSide"
        
        otherOwner.getName() >> "OtherEntity"

        ((GrailsHibernatePersistentProperty)gormOneToOne).getMappedForm() >> new PropertyConfig()

        when:
        def hibernateOneToOne = binder.bindOneToOne(gormOneToOne as GormOneToOne, ownerRoot, null, "")

        then:
        hibernateOneToOne instanceof HibernateOneToOne
        !hibernateOneToOne.isConstrained()
        hibernateOneToOne.getForeignKeyType() == ForeignKeyDirection.TO_PARENT
        hibernateOneToOne.isAlternateUniqueKey()
        hibernateOneToOne.getFetchMode() == FetchMode.DEFAULT
        hibernateOneToOne.getReferencedEntityName() == "OtherEntity"
        hibernateOneToOne.getPropertyName() == "myOneToOne"
        hibernateOneToOne.getReferencedPropertyName() == "otherSide"
    }

    def "should bind constrained one-to-one mapping when other side is hasOne"() {
        given:
        def metadataBuildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        def ownerRoot = new RootClass(metadataBuildingContext)
        
        def gormOneToOne = Mock(GormOneToOne, additionalInterfaces: [GrailsHibernatePersistentProperty])
        def otherSide = Mock(GormOneToOne)
        def owner = Mock(PersistentEntity)
        def otherOwner = Mock(PersistentEntity)

        gormOneToOne.getInverseSide() >> otherSide
        gormOneToOne.getName() >> "myOneToOne"
        gormOneToOne.getOwner() >> owner
        
        otherSide.isHasOne() >> true
        otherSide.getOwner() >> otherOwner
        
        otherOwner.getName() >> "OtherEntity"

        def propertyConfig = new PropertyConfig()
        ((GrailsHibernatePersistentProperty)gormOneToOne).getMappedForm() >> propertyConfig

        when:
        def hibernateOneToOne = binder.bindOneToOne(gormOneToOne as GormOneToOne, ownerRoot, null, "")

        then:
        hibernateOneToOne.isConstrained()
        hibernateOneToOne.getForeignKeyType() == ForeignKeyDirection.FROM_PARENT
        hibernateOneToOne.getReferencedEntityName() == "OtherEntity"
    }

    def "should respect fetch mode from mapping"() {
        given:
        def metadataBuildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        def ownerRoot = new RootClass(metadataBuildingContext)
        
        def gormOneToOne = Mock(GormOneToOne, additionalInterfaces: [GrailsHibernatePersistentProperty])
        def otherSide = Mock(GormOneToOne)
        def owner = Mock(PersistentEntity)
        def otherOwner = Mock(PersistentEntity)
        
        def propertyConfig = new PropertyConfig()
        propertyConfig.setFetch("join")

        gormOneToOne.getInverseSide() >> otherSide
        gormOneToOne.getOwner() >> owner
        otherSide.getOwner() >> otherOwner
        otherSide.isHasOne() >> false
        
        ((GrailsHibernatePersistentProperty)gormOneToOne).getMappedForm() >> propertyConfig

        when:
        def hibernateOneToOne = binder.bindOneToOne(gormOneToOne as GormOneToOne, ownerRoot, null, "")

        then:
        hibernateOneToOne.getFetchMode() == FetchMode.JOIN
    }
}
