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
package org.grails.orm.hibernate.connections

import grails.gorm.annotation.Entity
import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.core.exceptions.ConfigurationException
import org.grails.orm.hibernate.cfg.HibernateMappingContext
import org.hibernate.cfg.Configuration
import org.hibernate.dialect.H2Dialect
import org.springframework.context.support.StaticMessageSource

/**
 * Specs for {@link HibernateConnectionSourceFactory} using the shared H2 datastore
 * infrastructure from {@link HibernateGormDatastoreSpec}.
 */
class HibernateConnectionSourceFactorySpec extends HibernateGormDatastoreSpec {

    def setupSpec() {
        manager.addAllDomainClasses([Foo])
    }

    private static Map<String, String> h2Config() {
        [
            'dataSource.url'         : "jdbc:h2:mem:grailsDB;LOCK_TIMEOUT=10000",
            'dataSource.dbCreate'    : 'update',
            'dataSource.dialect'     : H2Dialect.name,
            'dataSource.formatSql'   : 'true',
            'hibernate.flush.mode'   : 'COMMIT',
            'hibernate.cache.queries': 'true',
            'hibernate.hbm2ddl.auto' : 'create',
        ]
    }

    void "Test hibernate connection factory creates an open session factory"() {
        when: "A factory is used to create a session factory"
        HibernateConnectionSourceFactory factory = new HibernateConnectionSourceFactory(Foo)
        def connectionSource = factory.create(ConnectionSource.DEFAULT, DatastoreUtils.createPropertyResolver(h2Config()))
        def query = connectionSource.source.getCriteriaBuilder().createQuery(Foo)
        query.select(query.from(Foo))

        then: "The session factory is created and queryable"
        connectionSource.source.openSession().createQuery(query).list().size() == 0

        when: "The connection source is closed"
        connectionSource.close()

        then: "The session factory is closed"
        connectionSource.source.isClosed()
    }

    void "getPersistentClasses returns the classes passed to the constructor"() {
        when:
        HibernateConnectionSourceFactory factory = new HibernateConnectionSourceFactory(Foo)

        then:
        factory.persistentClasses == [Foo] as Class[]
    }

    void "getMappingContext is a HibernateMappingContext populated with the entity after create()"() {
        given:
        HibernateConnectionSourceFactory factory = new HibernateConnectionSourceFactory(Foo)
        def connectionSource = factory.create(ConnectionSource.DEFAULT, DatastoreUtils.createPropertyResolver(h2Config()))

        expect:
        factory.mappingContext instanceof HibernateMappingContext
        factory.mappingContext.getPersistentEntity(Foo.name) != null

        cleanup:
        connectionSource?.close()
    }

    void "create() with a named connection source propagates the name"() {
        given:
        HibernateConnectionSourceFactory factory = new HibernateConnectionSourceFactory(Foo)
        def connectionSource = factory.create("secondary", DatastoreUtils.createPropertyResolver(h2Config()))

        expect:
        connectionSource.name == "secondary"

        cleanup:
        connectionSource?.close()
    }

    void "buildConfiguration throws ConfigurationException for a non-HibernateMappingContextConfiguration configClass"() {
        given: "Settings with a configClass that is not a subclass of HibernateMappingContextConfiguration"
        HibernateConnectionSourceFactory factory = new HibernateConnectionSourceFactory(Foo)
        def settings = new HibernateConnectionSourceSettings()
        settings.hibernate.configClass = Configuration  // plain Configuration, not the subclass

        // Provide a minimal DataSource connection source to drive buildConfiguration
        def dsConfig = [
            'dataSource.url'        : "jdbc:h2:mem:grailsDB;LOCK_TIMEOUT=10000",
            'dataSource.dbCreate'   : 'update',
            'dataSource.dialect'    : H2Dialect.name,
            'hibernate.hbm2ddl.auto': 'create',
        ]
        def dataSourceCs = new org.grails.datastore.gorm.jdbc.connections.DataSourceConnectionSourceFactory()
            .create(ConnectionSource.DEFAULT, DatastoreUtils.createPropertyResolver(dsConfig))

        when:
        factory.buildConfiguration(ConnectionSource.DEFAULT, dataSourceCs, settings)

        then:
        thrown(ConfigurationException)
    }

    void "setMessageSource stores the provided message source"() {
        given:
        HibernateConnectionSourceFactory factory = new HibernateConnectionSourceFactory(Foo)
        def source = new StaticMessageSource()

        when:
        factory.setMessageSource(source)

        then:
        factory.messageSource.is(source)
    }

    void "the shared datastore mapping context has Foo registered as a persistent entity"() {
        expect:
        getMappingContext().getPersistentEntity(Foo.name) != null
    }
}

@Entity
class Foo {
    String name
}
