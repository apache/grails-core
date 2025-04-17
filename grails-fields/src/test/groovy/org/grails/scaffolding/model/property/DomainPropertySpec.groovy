package org.grails.scaffolding.model.property

import org.grails.orm.hibernate.cfg.HibernateMappingContext
import org.grails.scaffolding.model.MocksDomain
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.Embedded
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

/**
 * Created by Jim on 6/7/2016.
 */
@Subject(DomainPropertyImpl)
class DomainPropertySpec extends Specification implements MocksDomain {

    @Shared
    MappingContext mappingContext

    @Shared
    PersistentEntity domainClass

    @Shared
    PersistentProperty address

    @Shared
    PersistentProperty name

    @Shared
    PersistentProperty foos

    @Shared
    Embedded props

    void setup() {
        mappingContext = new KeyValueMappingContext("test")
        domainClass = mockDomainClass(mappingContext, ScaffoldedDomain)
        address = domainClass.getPropertyByName("address")
        props = (Embedded)domainClass.getPropertyByName("props")
        name = props.associatedEntity.getPropertyByName("name")
        foos = domainClass.getPropertyByName("foos")
    }

    void "test pathFromRoot"() {
        given:
        DomainProperty property

        when:
        property = new DomainPropertyImpl(address, mappingContext)

        then:
        property.pathFromRoot == "address"

        when:
        property = new DomainPropertyImpl(props, name, mappingContext)

        then:
        property.pathFromRoot == "props.name"
    }

    void "test bean type"() {
        given:
        DomainProperty property

        when:
        property = new DomainPropertyImpl(address, mappingContext)

        then:
        property.rootBeanType == ScaffoldedDomain
        property.beanType == ScaffoldedDomain

        when:
        property = new DomainPropertyImpl(props, name, mappingContext)

        then:
        property.rootBeanType == ScaffoldedDomain
        property.beanType == EmbeddedClass
    }

    void "test associated type"() {
        given:
        DomainProperty property

        when:
        property = new DomainPropertyImpl(address, mappingContext)

        then:
        property.associatedType == null

        when:
        property = new DomainPropertyImpl(foos, mappingContext)

        then:
        property.associatedType == String
    }

    @Unroll
    void "test isRequired #propertyName is required: #expected"() {
        given:
        DomainProperty property

        when:
        property = new DomainPropertyImpl(domainClass.getPropertyByName(propertyName), mappingContext)
        property.convertEmptyStringsToNull = convertEmpty
        property.trimStrings = trimStrings

        then:
        property.isRequired() == expected

        where:
        propertyName    | convertEmpty | trimStrings | expected
        "testRequired1" | true         | true        | true
        "testRequired1" | false        | true        | true
        "testRequired1" | true         | false       | true
        "testRequired2" | true         | true        | false
        "testRequired2" | false        | true        | false
        "testRequired2" | true         | false       | false
        "testRequired3" | true         | true        | false
        "testRequired3" | false        | true        | false
        "testRequired3" | true         | false       | false
        "testRequired4" | true         | true        | true
        "testRequired4" | false        | true        | false
        "testRequired4" | true         | false       | false
    }

    void "test getLabelKeys"() {
        given:
        DomainProperty property

        when:
        property = new DomainPropertyImpl(address, mappingContext)

        then:
        property.labelKeys == ["scaffoldedDomain.address.label"]

        when:
        property = new DomainPropertyImpl(props, name, mappingContext)

        then:
        property.labelKeys == ["embeddedClass.name.label", "scaffoldedDomain.props.name.label"]
    }

    void "test getDefaultLabel"() {
        given:
        DomainProperty property

        when:
        property = new DomainPropertyImpl(Stub(PersistentProperty) { getName() >> "fooBar" }, mappingContext)

        then:
        property.defaultLabel == "Foo Bar"
    }

    void "test sort"() {
        given:
        Embedded property = (Embedded)mappingContext.addExternalPersistentEntity(ScaffoldedDomainEntity).getPropertyByName("props")
        List<DomainProperty> properties = property.associatedEntity.persistentProperties.collect {
            new DomainPropertyImpl(it, mappingContext)
        }
        properties.sort()

        expect:
        properties[0].name == "firstName"
        properties[1].name == "lastName"
        properties.size() == 2
    }

    void "test sort w/ Hibernate embedded"() {
        given:
        List<DomainProperty> properties = new HibernateMappingContext().createEmbeddedEntity(EmbeddedClassEntity).persistentProperties.collect {
            new DomainPropertyImpl(it, mappingContext)
        }
        properties.sort()

        expect:
        properties[0].name == "firstName"
        properties[1].name == "lastName"
        properties.size() == 2
    }

    class ScaffoldedDomain {
        Long id
        Long version
        String address
        EmbeddedClass props

        String testRequired1
        String testRequired2
        String testRequired3
        String testRequired4

        Set<String> foos
        static hasMany = [foos: String]

        static embedded = ['props']

        static constraints = {
            testRequired1(nullable: false, blank: false)
            testRequired2(nullable: false, blank: true)
            testRequired3(nullable: true, blank: false)
        }
    }

    class ScaffoldedDomainEntity {
        Long id
        Long version
        EmbeddedClassEntity props
        static embedded = ['props']
    }

    class EmbeddedClass {
        String name
    }

    class EmbeddedClassEntity {
        String lastName
        String firstName
    }
}
