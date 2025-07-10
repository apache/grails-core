package org.grails.orm.hibernate.cfg.domainbinding

import grails.gorm.specs.HibernateGormDatastoreSpec
import org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy
import org.hibernate.boot.model.naming.PhysicalNamingStrategy

class NamingStrategyProviderSpec extends HibernateGormDatastoreSpec {

    void "Test constructor initializes with default strategy"() {
        when:
        def provider = new NamingStrategyProvider()
        def strategy = provider.getPhysicalNamingStrategy("sessionFactory")

        then:
        strategy instanceof CamelCaseToUnderscoresNamingStrategy
    }

    void "Test configureNamingStrategy with null strategy throws exception"() {
        given:
        def provider = new NamingStrategyProvider()

        when:
        provider.configureNamingStrategy("test", null)

        then:
        thrown(IllegalArgumentException)
    }

    void "Test configureNamingStrategy with PhysicalNamingStrategy instance"() {
        given:
        def provider = new NamingStrategyProvider()
        def mockStrategy = new MockPhysicalNamingStrategy()

        when:
        provider.configureNamingStrategy("test", mockStrategy)
        def strategy = provider.getPhysicalNamingStrategy("sessionFactory_test")

        then:
        strategy instanceof MockPhysicalNamingStrategy
    }

    void "Test configureNamingStrategy with Class"() {
        given:
        def provider = new NamingStrategyProvider()

        when:
        provider.configureNamingStrategy("test", MockPhysicalNamingStrategy)
        def strategy = provider.getPhysicalNamingStrategy("sessionFactory_test")

        then:
        strategy instanceof MockPhysicalNamingStrategy
    }

    void "Test configureNamingStrategy with class name"() {
        given:
        def provider = new NamingStrategyProvider()

        when:
        provider.configureNamingStrategy("test", MockPhysicalNamingStrategy.name)
        def strategy = provider.getPhysicalNamingStrategy("sessionFactory_test")

        then:
        strategy instanceof MockPhysicalNamingStrategy
    }

    void "Test getPhysicalNamingStrategy with default session factory"() {
        given:
        def provider = new NamingStrategyProvider()

        when:
        def strategy = provider.getPhysicalNamingStrategy("sessionFactory")

        then:
        strategy instanceof CamelCaseToUnderscoresNamingStrategy
    }

    void "Test getPhysicalNamingStrategy with custom session factory"() {
        given:
        def provider = new NamingStrategyProvider()
        def mockStrategy = new MockPhysicalNamingStrategy()
        provider.configureNamingStrategy("custom", mockStrategy)

        when:
        def strategy = provider.getPhysicalNamingStrategy("sessionFactory_custom")

        then:
        strategy instanceof MockPhysicalNamingStrategy
    }
}

class MockPhysicalNamingStrategy implements PhysicalNamingStrategy {
    @Override
    org.hibernate.boot.model.naming.Identifier toPhysicalCatalogName(org.hibernate.boot.model.naming.Identifier name, org.hibernate.engine.jdbc.env.spi.JdbcEnvironment jdbcEnvironment) {
        return name
    }

    @Override
    org.hibernate.boot.model.naming.Identifier toPhysicalSchemaName(org.hibernate.boot.model.naming.Identifier name, org.hibernate.engine.jdbc.env.spi.JdbcEnvironment jdbcEnvironment) {
        return name
    }

    @Override
    org.hibernate.boot.model.naming.Identifier toPhysicalTableName(org.hibernate.boot.model.naming.Identifier name, org.hibernate.engine.jdbc.env.spi.JdbcEnvironment jdbcEnvironment) {
        return name
    }

    @Override
    org.hibernate.boot.model.naming.Identifier toPhysicalSequenceName(org.hibernate.boot.model.naming.Identifier name, org.hibernate.engine.jdbc.env.spi.JdbcEnvironment jdbcEnvironment) {
        return name
    }

    @Override
    org.hibernate.boot.model.naming.Identifier toPhysicalColumnName(org.hibernate.boot.model.naming.Identifier name, org.hibernate.engine.jdbc.env.spi.JdbcEnvironment jdbcEnvironment) {
        return name
    }
}
