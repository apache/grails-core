package org.grails.orm.hibernate.cfg.domainbinding

import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.cfg.Identity
import org.hibernate.generator.GeneratorCreationContext
import org.hibernate.mapping.BasicValue
import org.hibernate.mapping.Column
import org.hibernate.mapping.Property
import org.hibernate.mapping.Table
import org.hibernate.mapping.Value
import spock.lang.Subject

class GrailsIdentityGeneratorSpec extends HibernateGormDatastoreSpec {

    def "should configure identity generator and set column as identity"() {
        given:
        def context = Mock(GeneratorCreationContext)
        def mappedId = new Identity()
        mappedId.setParams([foo: 'bar'])
        
        def table = new Table("test")
        def hibernateProperty = new Property()
        def value = new BasicValue(getGrailsDomainBinder().getMetadataBuildingContext(), table)
        def column = new Column("test_id")
        value.addColumn(column)
        hibernateProperty.setValue(value)
        
        context.getProperty() >> hibernateProperty
        
        when:
        @Subject
        def generator = new GrailsIdentityGenerator(context, mappedId)

        then:
        column.isIdentity() == true
        generator != null
    }

    def "should handle null mappedId gracefully"() {
        given:
        def context = Mock(GeneratorCreationContext)
        
        def table = new Table("test")
        def hibernateProperty = new Property()
        def value = new BasicValue(getGrailsDomainBinder().getMetadataBuildingContext(), table)
        def column = new Column("test_id2")
        value.addColumn(column)
        hibernateProperty.setValue(value)
        
        context.getProperty() >> hibernateProperty
        
        when:
        @Subject
        def generator = new GrailsIdentityGenerator(context, null)

        then:
        column.isIdentity() == true
        generator != null
    }
}
