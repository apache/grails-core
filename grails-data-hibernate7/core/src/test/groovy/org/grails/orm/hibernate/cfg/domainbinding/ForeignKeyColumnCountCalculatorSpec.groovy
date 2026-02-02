package org.grails.orm.hibernate.cfg.domainbinding

import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.ToOne
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentProperty
import org.grails.orm.hibernate.cfg.HibernatePersistentEntity
import spock.lang.Specification
import spock.lang.Unroll

class ForeignKeyColumnCountCalculatorSpec extends Specification {

    @Unroll
    def "Test calculateForeignKeyColumnCount with #scenario"() {
        given:
        def calculator = new ForeignKeyColumnCountCalculator()
        def refDomainClass = Mock(HibernatePersistentEntity) as PersistentEntity

        // Mock for a simple property
        def simpleProp = Mock(PersistentProperty)
        refDomainClass.getPropertyByName("simple") >> simpleProp

        // Mocks for a ToOne association with a simple ID
        def toOneSimpleIdProp = Mock(ToOne)
        def associatedEntitySimpleId = Mock(HibernatePersistentEntity)
        refDomainClass.getPropertyByName("toOneSimple") >> toOneSimpleIdProp
        toOneSimpleIdProp.getAssociatedEntity() >> associatedEntitySimpleId
        associatedEntitySimpleId.getCompositeIdentity() >> null

        // Mocks for a ToOne association with a composite ID of length 2
        def toOneCompositeIdProp = Mock(ToOne)
        def associatedEntityCompositeId = Mock(HibernatePersistentEntity)
        def compositeId = [Mock(GrailsHibernatePersistentProperty), Mock(GrailsHibernatePersistentProperty)] as GrailsHibernatePersistentProperty[]
        refDomainClass.getPropertyByName("toOneComposite") >> toOneCompositeIdProp
        toOneCompositeIdProp.getAssociatedEntity() >> associatedEntityCompositeId
        associatedEntityCompositeId.getCompositeIdentity() >> compositeId

        when:
        int columnCount = calculator.calculateForeignKeyColumnCount(refDomainClass, propertyNames as String[])

        then:
        columnCount == expectedCount

        where:
        scenario                                | propertyNames                                | expectedCount
        "a single simple property"              | ["simple"]                                   | 1
        "a ToOne with a simple ID"              | ["toOneSimple"]                              | 1
        "a ToOne with a composite ID"           | ["toOneComposite"]                           | 2
        "a mix of all property types"           | ["simple", "toOneSimple", "toOneComposite"] | 4
        "multiple simple properties"            | ["simple", "simple"]                         | 2
        "multiple composite ID properties"      | ["toOneComposite", "toOneComposite"]         | 4
    }
}
