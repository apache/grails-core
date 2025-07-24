package org.grails.orm.hibernate.cfg.domainbinding

import grails.gorm.specs.HibernateGormDatastoreSpec
import grails.persistence.Entity
import org.grails.datastore.mapping.model.PersistentProperty
import org.hibernate.boot.model.naming.Identifier
import org.hibernate.boot.model.naming.PhysicalNamingStrategy
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment
import spock.lang.Subject
import spock.lang.Unroll

import static org.grails.orm.hibernate.cfg.GrailsDomainBinder.FOREIGN_KEY_SUFFIX

/**
 * Specification for the NamingStrategyWrapper.
 *
 * Verifies that the wrapper correctly delegates calls to the underlying
 * PhysicalNamingStrategy and correctly implements its own composite logic.
 */
class NamingStrategyWrapperSpec extends HibernateGormDatastoreSpec {

    // Corrected: Removed @Shared. Mocks will be created fresh for each test.
    def mockStrategy
    def mockJdbcEnv

    @Subject
    def wrapper

    // Corrected: Use a setup() method to ensure each test gets a fresh
    // set of mocks and a fresh subject, preventing test interference.
    def setup() {
        mockStrategy = Mock(PhysicalNamingStrategy)
        mockJdbcEnv = Mock(JdbcEnvironment)
        wrapper = new NamingStrategyWrapper(mockStrategy, mockJdbcEnv)
    }

    @Unroll
    def "should throw an exception if a constructor argument is null"() {
        // The 'given:' block is no longer needed here, as the mocks are
        // created directly in the 'where' block.
        when: "A wrapper is constructed with a null #argName"
        new NamingStrategyWrapper(strategy, jdbcEnv)

        then: "An IllegalArgumentException is thrown"
        thrown(IllegalArgumentException)

        where:
        // Corrected: Mocks are now created directly in the data table.
        argName         | strategy                      | jdbcEnv
        "strategy"      | null                          | Mock(JdbcEnvironment)
        "jdbcEnv"       | Mock(PhysicalNamingStrategy)  | null
    }

    def "should delegate getColumnName to the wrapped strategy"() {
        given: "A logical column name and a captured argument"
        def logicalName = "firstName"
        def expectedPhysicalName = "first_name"
        def capturedIdentifier

        and: "The wrapped strategy is configured to capture its argument and return a physical identifier"
        mockStrategy.toPhysicalColumnName(_, mockJdbcEnv) >> { Identifier id, JdbcEnvironment env ->
            capturedIdentifier = id
            return Identifier.toIdentifier(expectedPhysicalName)
        }

        when: "The wrapper's getColumnName method is called"
        def actualResult = wrapper.getColumnName(logicalName)

        then: "The result from the wrapped strategy is returned"
        actualResult == expectedPhysicalName

        and: "The wrapped strategy was called with an identifier based on the logical name"
        capturedIdentifier.text == logicalName
    }

    def "should use logical column name when wrapped strategy returns null"() {
        given: "A logical column name"
        def logicalName = "firstName"

        and: "The wrapped strategy is configured to return null"
        mockStrategy.toPhysicalColumnName(_, mockJdbcEnv) >> null

        when: "The wrapper's getColumnName method is called"
        def actualResult = wrapper.getColumnName(logicalName)

        then: "The original logical name is returned, fulfilling the contract"
        actualResult == logicalName
    }

    def "should delegate getTableName to the wrapped strategy"() {
        given: "A logical table name and a captured argument"
        def logicalName = "MyTable"
        def expectedPhysicalName = "my_table"
        def capturedIdentifier

        and: "The wrapped strategy is configured to capture its argument and return a physical identifier"
        mockStrategy.toPhysicalTableName(_, mockJdbcEnv) >> { Identifier id, JdbcEnvironment env ->
            capturedIdentifier = id
            return Identifier.toIdentifier(expectedPhysicalName)
        }

        when: "The wrapper's getTableName method is called"
        def actualResult = wrapper.getTableName(logicalName)

        then: "The result from the wrapped strategy is returned"
        actualResult == expectedPhysicalName

        and: "The wrapped strategy was called with an identifier based on the logical name"
        capturedIdentifier.text == logicalName
    }

    def "should correctly generate a foreign key name for a property"() {
        given: "A persistent property and a captured argument"
        def ownerEntity = createPersistentEntity(Owner, getGrailsDomainBinder())
        PersistentProperty property = ownerEntity.getPropertyByName("someProperty")
        def capturedIdentifier

        and: "The wrapper's internal call to getColumnName is stubbed to capture its argument"
        def decapitalizedOwnerName = "owner"
        def physicalColumnName = "physical_owner_col"
        mockStrategy.toPhysicalColumnName(_, mockJdbcEnv) >> { Identifier id, JdbcEnvironment env ->
            capturedIdentifier = id
            return Identifier.toIdentifier(physicalColumnName)
        }

        when: "getForeignKeyForPropertyDomainClass is called"
        def actualFkName = wrapper.getForeignKeyForPropertyDomainClass(property)

        then: "The final name is the physical column name plus the standard suffix"
        actualFkName == physicalColumnName + FOREIGN_KEY_SUFFIX

        and: "The wrapped strategy was called with an identifier based on the decapitalized owner name"
        capturedIdentifier.text == decapitalizedOwnerName
    }
}

// Helper domain class for testing
@Entity
class Owner {
    Long id
    String someProperty
}