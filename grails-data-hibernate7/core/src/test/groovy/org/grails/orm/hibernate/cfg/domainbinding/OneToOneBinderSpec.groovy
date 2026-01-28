package org.grails.orm.hibernate.cfg.domainbinding

import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.datastore.mapping.model.types.OneToOne as GormOneToOne
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.orm.hibernate.cfg.PropertyConfig
import org.hibernate.FetchMode
import org.hibernate.mapping.OneToOne as HibernateOneToOne
import org.hibernate.mapping.RootClass
import org.hibernate.type.ForeignKeyDirection
import spock.lang.Subject

class OneToOneBinderSpec extends HibernateGormDatastoreSpec {

    @Subject
    OneToOneBinder binder

    PersistentPropertyToPropertyConfig mockConfigReader = Mock(PersistentPropertyToPropertyConfig)
    SimpleValueBinder mockSimpleValueBinder = Mock(SimpleValueBinder)

    def setup() {
        binder = new OneToOneBinder(getGrailsDomainBinder().getNamingStrategy(), mockConfigReader, mockSimpleValueBinder)
    }

    def "should bind one-to-one mapping with defaults"() {
        given:
        def metadataBuildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        def ownerRoot = new RootClass(metadataBuildingContext)
        def hibernateOneToOne = new HibernateOneToOne(metadataBuildingContext, null, ownerRoot)
        
        def gormOneToOne = GroovyMock(GormOneToOne)
        def otherSide = GroovyMock(GormOneToOne)
        def owner = GroovyMock(PersistentEntity)
        def otherOwner = GroovyMock(PersistentEntity)

        gormOneToOne.getInverseSide() >> otherSide
        gormOneToOne.getName() >> "myOneToOne"
        gormOneToOne.getOwner() >> owner
        
        otherSide.isHasOne() >> false
        otherSide.getOwner() >> otherOwner
        otherSide.getName() >> "otherSide"
        
        otherOwner.getName() >> "OtherEntity"

        mockConfigReader.toPropertyConfig(gormOneToOne) >> new PropertyConfig()

        when:
        binder.bindOneToOne(gormOneToOne, hibernateOneToOne, "")

        then:
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
        def hibernateOneToOne = new HibernateOneToOne(metadataBuildingContext, null, ownerRoot)
        
        def gormOneToOne = GroovyMock(GormOneToOne)
        def otherSide = GroovyMock(GormOneToOne)
        def owner = GroovyMock(PersistentEntity)
        def otherOwner = GroovyMock(PersistentEntity)

        gormOneToOne.getInverseSide() >> otherSide
        gormOneToOne.getName() >> "myOneToOne"
        gormOneToOne.getOwner() >> owner
        
        otherSide.isHasOne() >> true
        otherSide.getOwner() >> otherOwner
        
        otherOwner.getName() >> "OtherEntity"

        def propertyConfig = new PropertyConfig()
        mockConfigReader.toPropertyConfig(gormOneToOne) >> propertyConfig
        mockConfigReader.toPropertyConfig(otherSide) >> propertyConfig // In case SimpleValueBinder needs it too

        when:
        binder.bindOneToOne(gormOneToOne, hibernateOneToOne, "")

        then:
        hibernateOneToOne.isConstrained()
        hibernateOneToOne.getForeignKeyType() == ForeignKeyDirection.FROM_PARENT
        hibernateOneToOne.getReferencedEntityName() == "OtherEntity"
    }

    def "should respect fetch mode from mapping"() {
        given:
        def metadataBuildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        def ownerRoot = new RootClass(metadataBuildingContext)
        def hibernateOneToOne = new HibernateOneToOne(metadataBuildingContext, null, ownerRoot)
        
        def gormOneToOne = GroovyMock(GormOneToOne)
        def otherSide = GroovyMock(GormOneToOne)
        def owner = GroovyMock(PersistentEntity)
        def otherOwner = GroovyMock(PersistentEntity)
        
        def propertyConfig = new PropertyConfig()
        propertyConfig.setFetch("join")

        gormOneToOne.getInverseSide() >> otherSide
        gormOneToOne.getOwner() >> owner
        otherSide.getOwner() >> otherOwner
        otherSide.isHasOne() >> false
        
        mockConfigReader.toPropertyConfig(gormOneToOne) >> propertyConfig

        when:
        binder.bindOneToOne(gormOneToOne, hibernateOneToOne, "")

        then:
        hibernateOneToOne.getFetchMode() == FetchMode.JOIN
    }
}
