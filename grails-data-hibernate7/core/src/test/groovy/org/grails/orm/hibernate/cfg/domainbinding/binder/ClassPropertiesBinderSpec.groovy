package org.grails.orm.hibernate.cfg.domainbinding.binder

import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentProperty
import org.grails.orm.hibernate.cfg.Mapping
import org.grails.orm.hibernate.cfg.domainbinding.util.PropertyFromValueCreator
import org.hibernate.boot.spi.InFlightMetadataCollector
import org.hibernate.mapping.Property
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.Value

class ClassPropertiesBinderSpec extends HibernateGormDatastoreSpec {

    void "test bindClassProperties"() {
        given:
        def grailsPropertyBinder = Mock(GrailsPropertyBinder)
        def propertyFromValueCreator = Mock(PropertyFromValueCreator)
        def naturalIdentifierBinder = Mock(NaturalIdentifierBinder)
        def binder = new ClassPropertiesBinder(grailsPropertyBinder, propertyFromValueCreator, naturalIdentifierBinder)

        def domainClass = Mock(GrailsHibernatePersistentEntity)
        def persistentClass = new RootClass(getGrailsDomainBinder().getMetadataBuildingContext())
        persistentClass.setTable(new org.hibernate.mapping.Table("test"))
        def mappings = Mock(InFlightMetadataCollector)
        def sessionFactoryBeanName = "sessionFactory"

        def prop1 = Mock(GrailsHibernatePersistentProperty)
        prop1.getName() >> "prop1"
        def prop2 = Mock(GrailsHibernatePersistentProperty)
        prop2.getName() >> "prop2"
        domainClass.getPersistentPropertiesToBind() >> [prop1, prop2]
        
        def value1 = Mock(Value)
        def value2 = Mock(Value)
        
        def hibernateProp1 = new Property()
        hibernateProp1.setName("hibernateProp1")
        def hibernateProp2 = new Property()
        hibernateProp2.setName("hibernateProp2")
        
        def mapping = Mock(Mapping)
        domainClass.getMappedForm() >> mapping

        when:
        binder.bindClassProperties(domainClass, persistentClass, mappings)

        then:
        1 * grailsPropertyBinder.bindProperty(persistentClass, persistentClass.table, GrailsDomainBinder.EMPTY_PATH, null, prop1, mappings) >> value1
        1 * propertyFromValueCreator.createProperty(value1, prop1) >> hibernateProp1

        1 * grailsPropertyBinder.bindProperty(persistentClass, persistentClass.table, GrailsDomainBinder.EMPTY_PATH, null, prop2, mappings) >> value2
        1 * propertyFromValueCreator.createProperty(value2, prop2) >> hibernateProp2

        persistentClass.getProperty("hibernateProp1") == hibernateProp1
        persistentClass.getProperty("hibernateProp2") == hibernateProp2

        1 * naturalIdentifierBinder.bindNaturalIdentifier(mapping, persistentClass)
    }
}
