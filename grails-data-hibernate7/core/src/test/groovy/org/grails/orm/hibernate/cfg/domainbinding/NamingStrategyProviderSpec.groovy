/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.grails.orm.hibernate.cfg.domainbinding

import grails.gorm.specs.HibernateGormDatastoreSpec
import org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl
import org.hibernate.boot.model.naming.PhysicalNamingStrategy

import org.grails.orm.hibernate.cfg.domainbinding.util.NamingStrategyProvider

class NamingStrategyProviderSpec extends HibernateGormDatastoreSpec {

    void "Test constructor initializes with default strategy"() {
        when:
        def provider = new NamingStrategyProvider()
        def strategy = provider.getPhysicalNamingStrategy("sessionFactory")

        then:
        strategy instanceof PhysicalNamingStrategySnakeCaseImpl
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
        strategy instanceof PhysicalNamingStrategySnakeCaseImpl
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
