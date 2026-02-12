package org.grails.orm.hibernate.cfg.domainbinding

import org.hibernate.boot.model.naming.Identifier
import org.hibernate.boot.model.relational.Database
import org.hibernate.boot.model.relational.Namespace
import org.hibernate.boot.spi.InFlightMetadataCollector
import spock.lang.Specification
import spock.lang.Unroll

import org.grails.orm.hibernate.cfg.domainbinding.util.NamespaceNameExtractor

/**
 * Specification for the NamespaceNameExtractor utility.
 *
 * Verifies that the extractor can safely navigate the Hibernate
 * metadata object graph to find the default schema and catalog names.
 */
class NamespaceNameExtractorSpec extends Specification {

    // --- Tests for getSchemaName ---

    def "should return the schema name when the full object graph exists"() {
        given: "A complete chain of mock objects"
        def mockMappings = Mock(InFlightMetadataCollector)
        def mockDatabase = Mock(Database)
        def mockNamespace = Mock(Namespace)
        def mockSchemaIdentifier = Mock(Identifier)
        def namespaceName = new Namespace.Name(null, mockSchemaIdentifier)
        def expectedSchema = "my_schema"

        and: "The mocks are configured to return the next object in the chain"
        mockMappings.getDatabase() >> mockDatabase
        mockDatabase.getDefaultNamespace() >> mockNamespace
        mockNamespace.getName() >> namespaceName
        mockSchemaIdentifier.getCanonicalName() >> expectedSchema

        when: "the schema name is extracted"
        def result = NamespaceNameExtractor.getSchemaName(mockMappings)

        then: "the correct schema name is returned"
        result == expectedSchema
    }

    @Unroll
    def "getSchemaName should return null when #description is missing"() {
        given: "A chain of mocks configured to fail at a specific point"
        def mockMappings = Mock(InFlightMetadataCollector)
        def mockDatabase = Mock(Database)
        def mockNamespace = Mock(Namespace)
        def mockSchemaIdentifier = Mock(Identifier)
        def namespaceName = new Namespace.Name(null, mockSchemaIdentifier)

        and: "The mock chain is built only up to the point of failure"
        switch (failurePoint) {
            case 'database':
                mockMappings.getDatabase() >> null
                break
            case 'namespace':
                mockMappings.getDatabase() >> mockDatabase
                mockDatabase.getDefaultNamespace() >> null
                break
            case 'name':
                mockMappings.getDatabase() >> mockDatabase
                mockDatabase.getDefaultNamespace() >> mockNamespace
                mockNamespace.getName() >> null
                break
            case 'schema':
                mockMappings.getDatabase() >> mockDatabase
                mockDatabase.getDefaultNamespace() >> mockNamespace
                mockNamespace.getName() >> new Namespace.Name(null, null)
                break
        }

        when: "the schema name is extracted"
        def result = NamespaceNameExtractor.getSchemaName(mockMappings)

        then: "the result is null"
        result == null

        where:
        description             | failurePoint
        "the database"          | 'database'
        "the default namespace" | 'namespace'
        "the namespace name"    | 'name'
        "the schema identifier" | 'schema'
    }

    // --- Tests for getCatalogName ---

    def "should return the catalog name when the full object graph exists"() {
        given: "A complete chain of mock objects"
        def mockMappings = Mock(InFlightMetadataCollector)
        def mockDatabase = Mock(Database)
        def mockNamespace = Mock(Namespace)
        def mockCatalogIdentifier = Mock(Identifier)
        def namespaceName = new Namespace.Name(mockCatalogIdentifier, null)
        def expectedCatalog = "my_catalog"

        and: "The mocks are configured to return the next object in the chain"
        mockMappings.getDatabase() >> mockDatabase
        mockDatabase.getDefaultNamespace() >> mockNamespace
        mockNamespace.getName() >> namespaceName
        mockCatalogIdentifier.getCanonicalName() >> expectedCatalog

        when: "the catalog name is extracted"
        def result = NamespaceNameExtractor.getCatalogName(mockMappings)

        then: "the correct catalog name is returned"
        result == expectedCatalog
    }

    @Unroll
    def "getCatalogName should return null when #description is missing"() {
        given: "A chain of mocks configured to fail at a specific point"
        def mockMappings = Mock(InFlightMetadataCollector)
        def mockDatabase = Mock(Database)
        def mockNamespace = Mock(Namespace)

        and: "The mock chain is built only up to the point of failure"
        switch (failurePoint) {
            case 'database':
                mockMappings.getDatabase() >> null
                break
            case 'namespace':
                mockMappings.getDatabase() >> mockDatabase
                mockDatabase.getDefaultNamespace() >> null
                break
            case 'name':
                mockMappings.getDatabase() >> mockDatabase
                mockDatabase.getDefaultNamespace() >> mockNamespace
                mockNamespace.getName() >> null
                break
            case 'catalog':
                mockMappings.getDatabase() >> mockDatabase
                mockDatabase.getDefaultNamespace() >> mockNamespace
                mockNamespace.getName() >> new Namespace.Name(null, null)
                break
        }

        when: "the catalog name is extracted"
        def result = NamespaceNameExtractor.getCatalogName(mockMappings)

        then: "the result is null"
        result == null

        where:
        description              | failurePoint
        "the database"           | 'database'
        "the default namespace"  | 'namespace'
        "the namespace name"     | 'name'
        "the catalog identifier" | 'catalog'
    }

}