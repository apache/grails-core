package org.grails.orm.hibernate.cfg.domainbinding

import grails.gorm.specs.HibernateGormDatastoreSpec
import grails.persistence.Entity
import org.grails.orm.hibernate.cfg.HibernatePersistentEntity
import org.grails.orm.hibernate.cfg.Mapping
import org.hibernate.type.descriptor.java.BasicJavaType
import org.hibernate.type.descriptor.jdbc.JdbcType
import org.hibernate.usertype.BaseUserTypeSupport

import java.util.function.BiConsumer

class TypeNameProviderSpec extends HibernateGormDatastoreSpec {

    void "Test - type not a class"() {
        when:
        def grailsDomainBinder = getGrailsDomainBinder()
        def simpleName = "Book"
        def fieldProperties = ["name": String]
        def mappingProperties = ["name": "type: 'text'"]
        def persistentEntity = createPersistentEntity(grailsDomainBinder, simpleName, fieldProperties, mappingProperties)
        def property = persistentEntity.getPersistentProperties()[0]
        Mapping mapping = new Mapping()
        mapping.setUserTypes(["foo.Bar": persistentEntity.getJavaClass()])
        def name = new TypeNameProvider().getTypeName(property, mapping)

        then:
        name == "text"

    }

    void "Test - type is a class"() {
        when:
        def grailsDomainBinder = getGrailsDomainBinder()
        def simpleName = "Book"
        def fieldProperties = ["name": String]
        def mappingProperties = ["name": "type: String"]
        def persistentEntity = createPersistentEntity(grailsDomainBinder, simpleName, fieldProperties, mappingProperties)
        def property = persistentEntity.getPersistentProperties()[0]
        Mapping mapping = new Mapping()
        def name = new TypeNameProvider().getTypeName(property, mapping)

        then:
        name == "java.lang.String"

    }

    void "Test - type not included but in general mapping"() {
        when:
        def grailsDomainBinder = getGrailsDomainBinder()
        def persistentEntity = getMappingContext().addPersistentEntity(Employee) as HibernatePersistentEntity
        grailsDomainBinder.evaluateMapping(persistentEntity)
        def property = persistentEntity.getPersistentProperties()[0]
        Mapping mapping = new Mapping()
        mapping.setUserTypes([(Salary): SalaryType])
        def name = new TypeNameProvider().getTypeName(property, mapping)

        then:
        name == SalaryType.name

    }

}

class Salary {
    BigDecimal amount
}

class SalaryType extends  BaseUserTypeSupport<Salary> {
    @Override
    protected void resolve(BiConsumer<BasicJavaType<Salary>, JdbcType> resolutionConsumer) {
    }
}
@Entity
class Employee {
    Salary salary
}


