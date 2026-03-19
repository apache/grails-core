/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.grails.data.hibernate7.core

import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import groovy.sql.Sql
import org.apache.grails.data.testing.tck.base.GrailsDataTckManager
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.multitenancy.resolvers.SystemPropertyTenantResolver
import org.grails.orm.hibernate.GrailsHibernateTransactionManager
import org.grails.orm.hibernate.HibernateDatastore
import org.grails.orm.hibernate.cfg.HibernateMappingContextConfiguration
import org.h2.Driver
import org.hibernate.SessionFactory
import org.hibernate.dialect.H2Dialect
import org.hibernate.dialect.PostgreSQLDialect
import org.springframework.beans.factory.DisposableBean
import org.springframework.context.ApplicationContext
import org.springframework.orm.hibernate5.SessionFactoryUtils
import org.springframework.orm.hibernate5.SessionHolder
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.DefaultTransactionDefinition
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.testcontainers.containers.PostgreSQLContainer
import spock.lang.Specification

class GrailsDataHibernate7TckManager extends GrailsDataTckManager {
    static PostgreSQLContainer postgres

    private void ensurePostgresStarted() {
        if (postgres == null && isDockerAvailable()) {
            postgres = new PostgreSQLContainer("postgres:16")
            postgres.start()
        }
    }

    static boolean isDockerAvailable() {
        def candidates = [
                System.getProperty('user.home') + '/.docker/run/docker.sock',
                '/var/run/docker.sock',
                System.getenv('DOCKER_HOST') ?: ''
        ]
        candidates.any { it && (new File(it).exists() || it.startsWith('tcp:')) }
    }

    GrailsApplication grailsApplication
    HibernateDatastore hibernateDatastore
    org.hibernate.Session hibernateSession
    GrailsHibernateTransactionManager transactionManager
    SessionFactory sessionFactory
    TransactionStatus transactionStatus
    HibernateMappingContextConfiguration hibernateConfig
    ApplicationContext applicationContext
    HibernateDatastore multiDataSourceDatastore
    HibernateDatastore multiTenantMultiDataSourceDatastore
    ConfigObject grailsConfig = new ConfigObject()
    boolean isTransactional = true
    Class<? extends Specification> currentSpec

    @Override
    void setup(Class<? extends Specification> spec) {
        this.currentSpec = spec
        cleanRegistry()
        super.setup(spec)
    }

    private boolean shouldUsePostgres() {
        if (currentSpec?.simpleName == 'WhereQueryConnectionRoutingSpec') {
            ensurePostgresStarted()
            boolean usePostgres = postgres != null
            System.out.println("TCK Manager: currentSpec=${currentSpec?.simpleName}, usePostgres=${usePostgres}")
            return usePostgres
        }
        return false
    }

    @Override
    Session createSession() {
        System.setProperty('hibernate7.gorm.suite', "true")
        grailsApplication = new DefaultGrailsApplication(domainClasses as Class[], new GroovyClassLoader(GrailsDataHibernate7TckManager.getClassLoader()))
        grailsConfig.dataSource.dbCreate = "create-drop"
        grailsConfig.hibernate.proxy_factory_class = "org.grails.orm.hibernate.proxy.ByteBuddyGroovyProxyFactory"
        if (shouldUsePostgres()) {
            grailsConfig.dataSource.url = postgres.getJdbcUrl()
            grailsConfig.dataSource.username = postgres.getUsername()
            grailsConfig.dataSource.password = postgres.getPassword()
            grailsConfig.dataSource.driverClassName = postgres.getDriverClassName()
            grailsConfig.hibernate.dialect = PostgreSQLDialect.name
        }
        if (grailsConfig) {
            grailsApplication.config.putAll(grailsConfig)
        }
        hibernateDatastore = new HibernateDatastore(DatastoreUtils.createPropertyResolver(grailsConfig), domainClasses as Class[])
        transactionManager = hibernateDatastore.getTransactionManager()
        sessionFactory = hibernateDatastore.sessionFactory
        if (transactionStatus == null && isTransactional) {
            transactionStatus = transactionManager.getTransaction(new DefaultTransactionDefinition())
        } else if (isTransactional) {
            throw new RuntimeException("new transaction started during active transaction")
        }
        if (!isTransactional) {
            hibernateSession = sessionFactory.openSession()
            TransactionSynchronizationManager.bindResource(sessionFactory, new SessionHolder(hibernateSession))
        } else {
            hibernateSession = sessionFactory.currentSession
        }

        return hibernateDatastore.connect()
    }

    @Override
    void destroy() {
        super.destroy()

        if (transactionStatus != null) {
            def tx = transactionStatus
            transactionStatus = null
            transactionManager.rollback(tx)
        }
        if (hibernateSession != null) {
            SessionFactoryUtils.closeSession((org.hibernate.Session) hibernateSession)
        }

        if (hibernateConfig != null) {
            hibernateConfig = null
        }
        if (hibernateDatastore != null) {
            hibernateDatastore.destroy()
        }
        grailsApplication = null
        hibernateDatastore = null
        hibernateSession = null
        transactionManager = null
        sessionFactory = null
        if (applicationContext instanceof DisposableBean) {
            applicationContext.destroy()
        }
        applicationContext = null
        shutdownInMemDb()
    }

    @Override
    boolean supportsMultipleDataSources() {
        true
    }

    @Override
    void setupMultiDataSource(Class... domainClasses) {
        if (currentSpec == null) {
            currentSpec = domainClasses.length > 0 ? domainClasses[0] : null // Fallback, not great
        }
        boolean usePostgres = shouldUsePostgres()
        Map config = [
                'dataSource.url'           : usePostgres ? postgres.getJdbcUrl() : "jdbc:h2:mem:tckDefaultDB;LOCK_TIMEOUT=10000",
                'dataSource.username'      : usePostgres ? postgres.getUsername() : "sa",
                'dataSource.password'      : usePostgres ? postgres.getPassword() : "",
                'dataSource.driverClassName': usePostgres ? postgres.getDriverClassName() : Driver.name,
                'dataSource.dbCreate'      : 'create-drop',
                'dataSource.dialect'       : usePostgres ? PostgreSQLDialect.name : H2Dialect.name,
                'dataSource.formatSql'     : 'true',
                'hibernate.flush.mode'     : 'COMMIT',
                'hibernate.cache.queries'  : 'true',
                'hibernate.hbm2ddl.auto'   : 'create-drop',
                'hibernate.proxy_factory_class' : 'org.grails.orm.hibernate.proxy.ByteBuddyGroovyProxyFactory',
                'dataSources.secondary'    : [
                        url: usePostgres ? postgres.getJdbcUrl() : "jdbc:h2:mem:tckSecondaryDB;LOCK_TIMEOUT=10000",
                        username: usePostgres ? postgres.getUsername() : "sa",
                        password: usePostgres ? postgres.getPassword() : "",
                        driverClassName: usePostgres ? postgres.getDriverClassName() : Driver.name
                ],
        ]
        multiDataSourceDatastore = new HibernateDatastore(
                DatastoreUtils.createPropertyResolver(config), domainClasses
        )
    }

    @Override
    void cleanupMultiDataSource() {
        if (multiDataSourceDatastore != null) {
            multiDataSourceDatastore.destroy()
            multiDataSourceDatastore = null
            if (!shouldUsePostgres()) {
                shutdownInMemDb('jdbc:h2:mem:tckDefaultDB')
                shutdownInMemDb('jdbc:h2:mem:tckSecondaryDB')
            }
        }
    }

    @Override
    def getServiceForConnection(Class serviceType, String connectionName) {
        multiDataSourceDatastore
                .getDatastoreForConnection(connectionName)
                .getService(serviceType)
    }

    @Override
    boolean supportsMultiTenantMultiDataSource() {
        true
    }

    @Override
    void setupMultiTenantMultiDataSource(Class... domainClasses) {
        boolean usePostgres = shouldUsePostgres()
        Map config = [
                'grails.gorm.multiTenancy.mode'            : MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR,
                'grails.gorm.multiTenancy.tenantResolverClass': SystemPropertyTenantResolver,
                'dataSource.url'                            : usePostgres ? postgres.getJdbcUrl() : "jdbc:h2:mem:tckMtDefaultDB;LOCK_TIMEOUT=10000",
                'dataSource.username'                       : usePostgres ? postgres.getUsername() : "sa",
                'dataSource.password'                       : usePostgres ? postgres.getPassword() : "",
                'dataSource.driverClassName'                : usePostgres ? postgres.getDriverClassName() : Driver.name,
                'dataSource.dbCreate'                       : 'create-drop',
                'dataSource.dialect'                        : usePostgres ? PostgreSQLDialect.name : H2Dialect.name,
                'dataSource.formatSql'                      : 'true',
                'hibernate.flush.mode'                      : 'COMMIT',
                'hibernate.cache.queries'                   : 'true',
                'hibernate.hbm2ddl.auto'                    : 'create-drop',
                'hibernate.proxy_factory_class'             : 'org.grails.orm.hibernate.proxy.ByteBuddyGroovyProxyFactory',
                'dataSources.secondary'                     : [
                        url: usePostgres ? postgres.getJdbcUrl() : "jdbc:h2:mem:tckMtSecondaryDB;LOCK_TIMEOUT=10000",
                        username: usePostgres ? postgres.getUsername() : "sa",
                        password: usePostgres ? postgres.getPassword() : "",
                        driverClassName: usePostgres ? postgres.getDriverClassName() : Driver.name
                ],
        ]
        multiTenantMultiDataSourceDatastore = new HibernateDatastore(
                DatastoreUtils.createPropertyResolver(config), domainClasses
        )
    }

    @Override
    void cleanupMultiTenantMultiDataSource() {
        if (multiTenantMultiDataSourceDatastore != null) {
            multiTenantMultiDataSourceDatastore.destroy()
            multiTenantMultiDataSourceDatastore = null
            if (!shouldUsePostgres()) {
                shutdownInMemDb('jdbc:h2:mem:tckMtDefaultDB')
                shutdownInMemDb('jdbc:h2:mem:tckMtSecondaryDB')
            }
        }
    }

    @Override
    def getServiceForMultiTenantConnection(Class serviceType, String connectionName) {
        multiTenantMultiDataSourceDatastore
                .getDatastoreForConnection(connectionName)
                .getService(serviceType)
    }

    private void shutdownInMemDb() {
        shutdownInMemDb('jdbc:h2:mem:grailsDb')
    }

    private void shutdownInMemDb(String url) {
        Sql sql = null
        try {
            sql = Sql.newInstance(url, 'sa', '', Driver.name)
            sql.executeUpdate('SHUTDOWN')
        } catch (e) {
            // already closed, ignore
        } finally {
            try { sql?.close() } catch (ignored) {}
        }
    }
}
