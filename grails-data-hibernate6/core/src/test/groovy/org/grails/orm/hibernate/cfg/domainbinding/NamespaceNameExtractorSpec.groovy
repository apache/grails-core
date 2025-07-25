package org.grails.orm.hibernate.cfg.domainbinding

import org.hibernate.boot.model.naming.Identifier
import org.hibernate.boot.model.relational.Database
import org.hibernate.boot.model.relational.Namespace
import org.hibernate.boot.spi.InFlightMetadataCollector
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

/**
 * Specification for the NamespaceNameExtractor utility.
 *
 * Verifies that the extractor can safely navigate the Hibernate
 * metadata object graph to find the default schema and catalog names.
 */
class NamespaceNameExtractorSpec extends Specification {

    @Subject
    NamespaceNameExtractor extractor = new NamespaceNameExtractor()

    // --- Tests for getSchemaName ---

    def "should return the schema name when the full object graph exists"() {
        given: "A complete chain of mock objects"
        def mockMappings = Mock(InFlightMetadataCollector)
        def mockDatabase = Mock(Database)
        def mockNamespace = Mock(Namespace)
        def mockNamespaceName = Mock(Namespace.Name)
        def mockSchemaIdentifier = Mock(Identifier)
        def expectedSchema = "my_schema"

        and: "The mocks are configured to return the next object in the chain"
        mockMappings.getDatabase() >> mockDatabase
        mockDatabase.getDefaultNamespace() >> mockNamespace
        mockNamespace.getName() >> mockNamespaceName
        mockNamespaceName.getSchema() >> mockSchemaIdentifier
        mockSchemaIdentifier.getCanonicalName() >> expectedSchema

        when: "the schema name is extracted"
        def result = extractor.getSchemaName(mockMappings)

        then: "the correct schema name is returned"
        result == expectedSchema
    }

    @Unroll
    def "getSchemaName should return null when #description is missing"() {
        given: "A chain of mocks configured to fail at a specific point"
        def mockMappings = Mock(InFlightMetadataCollector)
        def mockDatabase = Mock(Database)
        def mockNamespace = Mock(Namespace)
        def mockNamespaceName = Mock(Namespace.Name)

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
                mockNamespace.getName() >> mockNamespaceName
                mockNamespaceName.getSchema() >> null
                break
        }

        when: "the schema name is extracted"
        def result = extractor.getSchemaName(mockMappings)

        then: "the result is null"
        result == null

        where:
        description             | failurePoint
        "the database"          | 'database'
        "the default namespace" | 'namespace'
        "the namespace name"    | 'name'
        "the schema identifier" | 'schema'
    }

    def "getSchemaName should return null when the input mappings object is null"() {
        when: "the extractor is called with a null input"
        def result = extractor.getSchemaName(null)

        then: "the result is null"
        result == null
    }

    // --- Tests for getCatalogName ---

    def "should return the catalog name when the full object graph exists"() {
        given: "A complete chain of mock objects"
        def mockMappings = Mock(InFlightMetadataCollector)
        def mockDatabase = Mock(Database)
        def mockNamespace = Mock(Namespace)
        def mockNamespaceName = Mock(Namespace.Name)
        def mockCatalogIdentifier = Mock(Identifier)
        def expectedCatalog = "my_catalog"

        and: "The mocks are configured to return the next object in the chain"
        mockMappings.getDatabase() >> mockDatabase
        mockDatabase.getDefaultNamespace() >> mockNamespace
        mockNamespace.getName() >> mockNamespaceName
        mockNamespaceName.getCatalog() >> mockCatalogIdentifier
        mockCatalogIdentifier.getCanonicalName() >> expectedCatalog

        when: "the catalog name is extracted"
        def result = extractor.getCatalogName(mockMappings)

        then: "the correct catalog name is returned"
        result == expectedCatalog
    }

    @Unroll
    def "getCatalogName should return null when #description is missing"() {
        given: "A chain of mocks configured to fail at a specific point"
        def mockMappings = Mock(InFlightMetadataCollector)
        def mockDatabase = Mock(Database)
        def mockNamespace = Mock(Namespace)
        def mockNamespaceName = Mock(Namespace.Name)

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
                mockNamespace.getName() >> mockNamespaceName
                mockNamespaceName.getCatalog() >> null
                break
        }

        when: "the catalog name is extracted"
        def result = extractor.getCatalogName(mockMappings)

        then: "the result is null"
        result == null

        where:
        description              | failurePoint
        "the database"           | 'database'
        "the default namespace"  | 'namespace'
        "the namespace name"     | 'name'
        "the catalog identifier" | 'catalog'
    }

    def "getCatalogName should return null when the input mappings object is null"() {
        when: "the extractor is called with a null input"
        def result = extractor.getCatalogName(null)

        then: "the result is null"
        result == null
    }

}