package org.grails.orm.hibernate.cfg.domainbinding

import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.orm.hibernate.cfg.Mapping
import org.grails.orm.hibernate.cfg.PropertyConfig
import spock.lang.Subject

class ConfigureDerivedPropertiesConsumerSpec extends HibernateGormDatastoreSpec {

    def "should set derived to true if formula is present"() {
        given:
        def mapping = Mock(Mapping)
        def propConfig = new PropertyConfig()
        propConfig.setFormula("some SQL formula")
        
        def persistentProperty = Mock(PersistentProperty)
        persistentProperty.getName() >> "derivedProp"
        
        mapping.getPropertyConfig("derivedProp") >> propConfig
        
        @Subject
        def consumer = new ConfigureDerivedPropertiesConsumer(mapping)

        when:
        consumer.accept(persistentProperty)

        then:
        propConfig.isDerived() == true
    }

    def "should set derived to false if formula is null"() {
        given:
        def mapping = Mock(Mapping)
        def propConfig = new PropertyConfig()
        propConfig.setFormula(null)
        
        def persistentProperty = Mock(PersistentProperty)
        persistentProperty.getName() >> "nonDerivedProp"
        
        mapping.getPropertyConfig("nonDerivedProp") >> propConfig
        
        @Subject
        def consumer = new ConfigureDerivedPropertiesConsumer(mapping)

        when:
        consumer.accept(persistentProperty)

        then:
        propConfig.isDerived() == false
    }

    def "should do nothing if property configuration is missing"() {
        given:
        def mapping = Mock(Mapping)
        def persistentProperty = Mock(PersistentProperty)
        persistentProperty.getName() >> "missingProp"
        
        mapping.getPropertyConfig("missingProp") >> null
        
        @Subject
        def consumer = new ConfigureDerivedPropertiesConsumer(mapping)

        when:
        consumer.accept(persistentProperty)

        then:
        noExceptionThrown()
    }
}
