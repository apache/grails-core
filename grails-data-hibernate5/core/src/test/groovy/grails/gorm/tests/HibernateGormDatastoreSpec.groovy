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

package grails.gorm.tests

import org.apache.grails.data.hibernate5.core.GrailsDataHibernate5TckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.orm.hibernate.AbstractHibernateSession
import org.grails.orm.hibernate.HibernateDatastore
import org.grails.orm.hibernate.cfg.GrailsDomainBinder
import org.grails.orm.hibernate.cfg.HibernateMappingContext
import org.grails.orm.hibernate.cfg.HibernatePersistentEntity
import org.grails.orm.hibernate.query.HibernateQuery

import org.hibernate.boot.MetadataSources
import org.hibernate.boot.internal.BootstrapContextImpl
import org.hibernate.boot.internal.InFlightMetadataCollectorImpl
import org.hibernate.boot.internal.MetadataBuilderImpl
import org.hibernate.boot.registry.BootstrapServiceRegistry
import org.hibernate.boot.registry.StandardServiceRegistryBuilder
import org.hibernate.boot.registry.classloading.spi.ClassLoaderService
import org.hibernate.dialect.H2Dialect
import org.hibernate.internal.SessionFactoryImpl
import org.hibernate.service.spi.ServiceRegistryImplementor
import org.hibernate.boot.spi.MetadataContributor

/**
 * Base spec for Hibernate 5 integration tests. Sets up a per-spec H2 in-memory datastore
 * and registers domain classes via {@link #manager}. Individual specs call
 * {@code manager.registerDomainClasses(...)} in their own {@code setupSpec()} rather than
 * loading the entire package, keeping each spec isolated and fast.
 */
class HibernateGormDatastoreSpec extends GrailsDataTckSpec<GrailsDataHibernate5TckManager> {

    void setupSpec() {
        manager.grailsConfig = [
                'dataSource.url'               : "jdbc:h2:mem:grailsDB;LOCK_TIMEOUT=10000",
                'dataSource.dbCreate'          : 'create-drop',
                'dataSource.formatSql'         : 'true',
                'dataSource.logSql'            : 'true',
                'hibernate.flush.mode'         : 'COMMIT',
                'hibernate.cache.queries'      : 'true',
                'hibernate.hbm2ddl.auto'       : 'create',
                'hibernate.jpa.compliance.cascade': 'true',
        ]
    }

    HibernatePersistentEntity createPersistentEntity(GrailsDomainBinder binder
                                                    , String className
                                                     , Map<String, Class> fieldProperties
                                                     , Map<String, String> staticMapping

    ) {
        def classLoader = new GroovyClassLoader()
        def classText = """
        package foo
        import grails.gorm.annotation.Entity
        import grails.gorm.hibernate.HibernateEntity
        @Entity
        class ${className} implements HibernateEntity<${className}> {

            ${fieldProperties.collect { name, type -> "${type.simpleName} ${name}" }.join('\n            ')}

            static mapping = {
                ${staticMapping.collect { name, value -> "${name} ${value}" }.join('\n            ')}
            }
        }
    """

        def clazz = classLoader.parseClass(classText)
        createPersistentEntity(clazz, binder)
    }

    HibernatePersistentEntity createPersistentEntity(Class clazz, GrailsDomainBinder binder) {
        def entity = getMappingContext().addPersistentEntity(clazz) as HibernatePersistentEntity
        binder.evaluateMapping(entity)
        entity
    }

    HibernatePersistentEntity createPersistentEntity(Class clazz) {
        return createPersistentEntity(clazz, getGrailsDomainBinder())
    }

    protected InFlightMetadataCollectorImpl getCollector() {
        def bootstrapServiceRegistry = getServiceRegistry()
                .getParentServiceRegistry()
                .getParentServiceRegistry() as BootstrapServiceRegistry
        def serviceRegistry = new StandardServiceRegistryBuilder(bootstrapServiceRegistry)
                .applySetting("hibernate.dialect", H2Dialect.class.getName())
                .applySetting("jakarta.persistence.jdbc.url", "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1")
                .applySetting("jakarta.persistence.jdbc.driver", "org.h2.Driver")
                .build()
        def options = new MetadataBuilderImpl(
                new MetadataSources(serviceRegistry)
        ).getMetadataBuildingOptions()
        new InFlightMetadataCollectorImpl(
                new BootstrapContextImpl( serviceRegistry, options)
                , options);
    }

    protected HibernateMappingContext getMappingContext() {
        manager.hibernateDatastore.getMappingContext()
    }

    protected GrailsDomainBinder getGrailsDomainBinder() {
        def registry = getServiceRegistry()
        registry
                .getParentServiceRegistry()
                .getService(ClassLoaderService.class)
                .loadJavaServices(MetadataContributor.class)
                .find { it instanceof GrailsDomainBinder }
    }

    protected ServiceRegistryImplementor getServiceRegistry() {
        getSessionFactory()
                .getServiceRegistry()
    }

    protected SessionFactoryImpl getSessionFactory() {
        manager.hibernateDatastore.sessionFactory as SessionFactoryImpl
    }

    protected HibernateDatastore getDatastore() {
        manager.hibernateDatastore
    }


    protected AbstractHibernateSession getSession() {
        datastore.connect() as AbstractHibernateSession
    }

    protected PersistentEntity getPersistentEntity(Class clazz) {
        getMappingContext().getPersistentEntity(clazz.typeName)
    }

    protected HibernateQuery getQuery(Class clazz) {
        return  new HibernateQuery(session, getPersistentEntity(clazz))
    }
}