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

package grails.gorm.specs

import org.apache.grails.data.hibernate7.core.GrailsDataHibernate7TckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.orm.hibernate.HibernateSession
import org.grails.orm.hibernate.HibernateDatastore
import org.grails.orm.hibernate.cfg.domainbinding.binder.GrailsDomainBinder
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.HibernateMappingContext
import org.grails.orm.hibernate.cfg.MappingCacheHolder
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
import org.hibernate.boot.spi.AdditionalMappingContributor

/**
 * The original GormDataStoreSpec destroyed the setup
 * between tests instead of at the end of all tests
 * It also wqs default configured for H2 which
 * made it break with some Java types.
 * Finally, it loaded all the test Entities,
 * now it can be setup individually.
 */
class HibernateGormDatastoreSpec extends GrailsDataTckSpec<GrailsDataHibernate7TckManager> {

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

    GrailsHibernatePersistentEntity createPersistentEntity(GrailsDomainBinder binder
                                                    , String className
                                                     , Map<String, Class> fieldProperties
                                                     , Map<String, String> staticMapping
                                                     , List<String> embeddedProps = []
                                                     , Map<String, Class> hasManyMap = [:]
                                                     , Map<String, Class> belongsToMap = [:]

    ) {
        def classLoader = new GroovyClassLoader()
        def classText = """
        package foo
        import grails.gorm.annotation.Entity
        import grails.gorm.hibernate.HibernateEntity
        @Entity
        class ${className} implements HibernateEntity<${className}> {

            ${fieldProperties.collect { name, type -> "${(type instanceof Class ? type : type.javaClass).name} ${name}" }.join('\n            ')}

            static embedded = ${embeddedProps.inspect()}
            static hasMany = [${hasManyMap.collect { name, type -> "${name}: ${(type instanceof Class ? type : type.javaClass).name}" }.join(', ')}]
            static belongsTo = [${belongsToMap.collect { name, type -> "${name}: ${(type instanceof Class ? type : type.javaClass).name}" }.join(', ')}]

            static mapping = {
                ${staticMapping.collect { name, value -> "${name} ${value}" }.join('\n            ')}
            }
        }
    """

        def clazz = classLoader.parseClass(classText)
        createPersistentEntity(clazz, binder)
    }

    GrailsHibernatePersistentEntity createPersistentEntity(Class clazz, GrailsDomainBinder binder) {
        def entity = getMappingContext().addPersistentEntity(clazz) as GrailsHibernatePersistentEntity
        if (entity != null) {
            getMappingContext().getMappingCacheHolder().cacheMapping(entity)
        }
        entity
    }

    GrailsHibernatePersistentEntity createPersistentEntity(Class clazz) {
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
                .loadJavaServices(AdditionalMappingContributor.class)
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


    protected HibernateSession getSession() {
        datastore.connect() as HibernateSession
    }

    protected PersistentEntity getPersistentEntity(Class clazz) {
        getMappingContext().getPersistentEntity(clazz.typeName)
    }

    protected HibernateQuery getQuery(Class clazz) {
        return  new HibernateQuery(session, getPersistentEntity(clazz))
    }

    /**
     * Triggers the first-pass Hibernate mapping for all registered entities.
     * This initializes the Hibernate Collection, Table, and Column objects
     * required for SecondPass binder tests.
     */
    protected void hibernateFirstPass() {
        def gdb = getGrailsDomainBinder()
        def collector = gdb.getMetadataBuildingContext().getMetadataCollector()
        gdb.contribute(collector, getMappingContext())
    }

    /**
     * Returns true when a Docker daemon is reachable on this machine.
     * <p>
     * Checks the well-known socket paths used by Docker Desktop on macOS and Linux.
     * Prefer this over calling {@code DockerClientFactory.instance().client()} directly,
     * which can throw a 500 error on macOS when the daemon API version doesn't match
     * the docker-java client version bundled with Testcontainers.
     */
    static boolean isDockerAvailable() {
        def candidates = [
            System.getProperty('user.home') + '/.docker/run/docker.sock',
            '/var/run/docker.sock',
            System.getenv('DOCKER_HOST') ?: ''
        ]
        candidates.any { it && new File(it).exists() }
    }
}
