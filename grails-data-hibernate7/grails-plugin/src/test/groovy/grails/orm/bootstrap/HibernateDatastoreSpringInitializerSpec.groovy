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
package grails.orm.bootstrap

import javax.sql.DataSource

import grails.gorm.annotation.Entity
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.orm.hibernate.HibernateDatastore
import org.hibernate.Session
import org.hibernate.SessionFactory
import org.hibernate.dialect.H2Dialect
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.beans.factory.support.RootBeanDefinition
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.support.GenericApplicationContext
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.transaction.PlatformTransactionManager
import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Created by graemerocher on 29/01/14.
 */
class HibernateDatastoreSpringInitializerSpec extends Specification{

    @AutoCleanup
    ConfigurableApplicationContext applicationContext

    void "Test configure multiple data sources"() {
        given:"An initializer instance"
        Map config = [
                'dataSource.url':"jdbc:h2:mem:people;LOCK_TIMEOUT=10000",
                'dataSource.dialect': H2Dialect.name,
                'dataSource.formatSql': 'true',
                'hibernate.flush.mode': 'COMMIT',
                'hibernate.cache.queries': 'true',
                'hibernate.hbm2ddl.auto': 'create',
                'dataSources.books.url':"jdbc:h2:mem:books;LOCK_TIMEOUT=10000",
                'dataSources.moreBooks.url':"jdbc:h2:mem:moreBooks;LOCK_TIMEOUT=10000"
        ]
        def datastoreInitializer = new HibernateDatastoreSpringInitializer(config, Person, Book, Author)

        when:"the application is configured"
        applicationContext = (ConfigurableApplicationContext) datastoreInitializer.configure()

        then:"Each session factory has the correct number of persistent entities"
        applicationContext.getBeansOfType(PlatformTransactionManager).size() == 3
        applicationContext.getBean("sessionFactory", SessionFactory).metamodel.entities.size() == 2
        applicationContext.getBean("sessionFactory", SessionFactory).metamodel.entity(Person.name)
        applicationContext.getBean("sessionFactory", SessionFactory).metamodel.entity(Author.name)
        applicationContext.getBean("sessionFactory_books", SessionFactory).metamodel.entities.size() == 2
        applicationContext.getBean("sessionFactory_books", SessionFactory).metamodel.entity(Book.name)
        applicationContext.getBean("sessionFactory_books", SessionFactory).metamodel.entity(Author.name)
        applicationContext.getBean("sessionFactory_moreBooks", SessionFactory).metamodel.entities.size() == 2
        applicationContext.getBean("sessionFactory_moreBooks", SessionFactory).metamodel.entity(Book.name)
        applicationContext.getBean("sessionFactory_moreBooks", SessionFactory).metamodel.entity(Author.name)

        and:"Each domain has the correct data source(s)"
        def hibernateDatastore = applicationContext.getBean(HibernateDatastore)
        Person.withNewSession { Person.count() == 0 }
        hibernateDatastore.withNewSession { Session s ->
            assert s.doReturningWork { it.getMetaData().getURL() } == "jdbc:h2:mem:people"
            return true
        }
        hibernateDatastore.withNewSession("books") { Session s ->
            assert s.doReturningWork { it.getMetaData().getURL() } == "jdbc:h2:mem:books"
            return true
        }
        hibernateDatastore.withNewSession("moreBooks") { Session s ->
            assert s.doReturningWork { it.getMetaData().getURL() } == "jdbc:h2:mem:moreBooks"
            return true
        }
        hibernateDatastore.withNewSession { Session s ->
            assert s.doReturningWork { it.getMetaData().getURL() } == "jdbc:h2:mem:people"
            return true
        }
        hibernateDatastore.withNewSession("books") { Session s ->
            assert s.doReturningWork { it.getMetaData().getURL() } == "jdbc:h2:mem:books"
            return true
        }
        Author.moreBooks.withNewSession { Session s ->
            assert s.doReturningWork { it.getMetaData().getURL() } == "jdbc:h2:mem:moreBooks"
            return true
        }
    }

    void "Test configureDataSources uses the customized default data source bean name consistently"() {
        given: "an initializer with a customized default data source bean name"
        def datastoreInitializer = new HibernateDatastoreSpringInitializer([:], Person)
        datastoreInitializer.defaultDataSourceBeanName = 'primary'

        when: "data sources are configured from a resolver with only the default data source present"
        datastoreInitializer.configureDataSources(DatastoreUtils.createPropertyResolver(['dataSource.url': 'jdbc:h2:mem:customDefaultDsName;LOCK_TIMEOUT=10000']))

        then: "the default entry is recorded under the custom name, not the literal ConnectionSource.DEFAULT"
        datastoreInitializer.dataSources == ['primary'] as Set<String>
    }

    void "Test the Map/Collection<Class> constructor bootstraps GORM"() {
        given: "An initializer built from a Collection of persistent classes"
        def datastoreInitializer = new HibernateDatastoreSpringInitializer(
                ['dataSource.url': 'jdbc:h2:mem:collectionCtor;LOCK_TIMEOUT=10000', 'hibernate.hbm2ddl.auto': 'create'],
                [Person] as Collection<Class>)

        when: "the application is configured"
        applicationContext = (ConfigurableApplicationContext) datastoreInitializer.configure()

        then: "GORM is bootstrapped with the given entity"
        applicationContext.getBean(HibernateDatastore).mappingContext.getPersistentEntity(Person.name) != null
        Person.withNewSession { Person.count() == 0 }
    }

    void "Test configureForDataSource bootstraps GORM around a pre-existing DataSource"() {
        given: "a DataSource created ahead of time"
        def dataSource = new DriverManagerDataSource(HibernateDatastoreSpringInitializer.TEST_DB_URL, 'sa', '')
        dataSource.driverClassName = 'org.h2.Driver'
        def datastoreInitializer = new HibernateDatastoreSpringInitializer(['hibernate.hbm2ddl.auto': 'create'], Person)

        when: "the initializer is configured around that DataSource"
        applicationContext = (ConfigurableApplicationContext) datastoreInitializer.configureForDataSource(dataSource)

        then: "the pre-existing DataSource is registered and reused rather than a new one being built"
        applicationContext.getBean(HibernateDatastoreSpringInitializer.DEFAULT_DATA_SOURCE_NAME, DataSource).is(dataSource)
        Person.withNewSession { Person.count() == 0 }
    }

    void "Test configureForBeanDefinitionRegistry throws when the hibernateDatastore bean was not registered"() {
        given: "an initializer whose bean definitions never register hibernateDatastore"
        def datastoreInitializer = new HibernateDatastoreSpringInitializer([:], Person) {
            @Override
            Closure getBeanDefinitions(BeanDefinitionRegistry beanDefinitionRegistry) {
                { -> }
            }
        }
        def registry = new GenericApplicationContext()

        when:
        datastoreInitializer.configureForBeanDefinitionRegistry(registry)

        then:
        thrown(IllegalStateException)

        cleanup:
        registry.close()
    }

    void "Test the OSIV interceptor is registered when the registry is a web application"() {
        given: "a registry that signals it belongs to a web application"
        def datastoreInitializer = new HibernateDatastoreSpringInitializer(['dataSource.url': 'jdbc:h2:mem:osivEnabled;LOCK_TIMEOUT=10000'], Person)
        def registry = new GenericApplicationContext()
        registry.registerBeanDefinition('grailsControllerHelper', new RootBeanDefinition(Object))

        when:
        datastoreInitializer.configureForBeanDefinitionRegistry(registry)
        registry.refresh()
        applicationContext = registry

        then:
        registry.containsBean('openSessionInViewInterceptor')
    }

    void "Test the OSIV interceptor is not registered for a non-web application registry"() {
        given: "An initializer instance configured against a plain, non-web registry"
        def datastoreInitializer = new HibernateDatastoreSpringInitializer(['dataSource.url': 'jdbc:h2:mem:osivDisabled;LOCK_TIMEOUT=10000'], Person)

        when:
        applicationContext = (ConfigurableApplicationContext) datastoreInitializer.configure()

        then:
        !applicationContext.containsBean('openSessionInViewInterceptor')
    }

    @Unroll
    void "the default connection source is the first of the data sources when #configured"() {
        when: 'an initializer is created'
        def datastoreInitializer = new HibernateDatastoreSpringInitializer(config, Person)

        then: 'the default connection source comes first, followed by the additional data sources in configuration order'
        datastoreInitializer.dataSources instanceof LinkedHashSet
        datastoreInitializer.dataSources as List == expected

        where:
        configured                        | config                                                 || expected
        'nothing is configured'           | [:]                                                    || [ConnectionSource.DEFAULT]
        'only dataSource is configured'   | ['dataSource.url': 'jdbc:h2:mem:orderDefault']         || [ConnectionSource.DEFAULT]
        'only dataSources are configured' | ['dataSources.books.url': 'jdbc:h2:mem:books',
                                             'dataSources.moreBooks.url': 'jdbc:h2:mem:moreBooks'] || [ConnectionSource.DEFAULT, 'books', 'moreBooks']
        'both are configured'             | ['dataSources.books.url': 'jdbc:h2:mem:books',
                                             'dataSource.url': 'jdbc:h2:mem:orderDefault',
                                             'dataSources.moreBooks.url': 'jdbc:h2:mem:moreBooks'] || [ConnectionSource.DEFAULT, 'books', 'moreBooks']
    }

    void "the default data source is registered as the dataSource bean when no dataSource is configured"() {
        given: 'an initializer without dataSource configuration'
        def datastoreInitializer = new HibernateDatastoreSpringInitializer([:], Person)

        when: 'the application is configured'
        applicationContext = (ConfigurableApplicationContext) datastoreInitializer.configure()
        def hibernateDatastore = applicationContext.getBean(HibernateDatastore)

        then: 'the data source GORM uses is available by name and by type'
        applicationContext.getBeansOfType(DataSource).keySet() == ['dataSource'] as Set
        applicationContext.getBean('dataSource', DataSource).is(hibernateDatastore.dataSource)
        applicationContext.getBean('dataSource', DataSource).connection.withCloseable { it.metaData.URL } ==
                'jdbc:h2:mem:grailsDB'
    }

    void "the default data source is registered as the dataSource bean when only additional data sources are configured"() {
        given: 'an initializer configuring only an additional data source'
        def datastoreInitializer = new HibernateDatastoreSpringInitializer([
                'dataSources.books.url': 'jdbc:h2:mem:defaultDataSourceBeanBooks;LOCK_TIMEOUT=10000'
        ], Person)

        when: 'the application is configured'
        applicationContext = (ConfigurableApplicationContext) datastoreInitializer.configure()

        then: 'both the default and the additional data source are beans'
        applicationContext.getBeansOfType(DataSource).keySet() == ['dataSource', 'dataSource_books'] as Set
        applicationContext.getBean('dataSource', DataSource).connection.withCloseable { it.metaData.URL } ==
                'jdbc:h2:mem:grailsDB'
        applicationContext.getBean('dataSource_books', DataSource).connection.withCloseable { it.metaData.URL } ==
                'jdbc:h2:mem:defaultDataSourceBeanBooks'
    }

    void "the data source passed to configureForDataSource remains the dataSource bean"() {
        given: 'a data source and an initializer without dataSource configuration'
        def dataSource = new DriverManagerDataSource('jdbc:h2:mem:configureForDataSource;LOCK_TIMEOUT=10000')
        def datastoreInitializer = new HibernateDatastoreSpringInitializer([:], Person)

        when: 'the application is configured for that data source'
        applicationContext = (ConfigurableApplicationContext) datastoreInitializer.configureForDataSource(dataSource)

        then: 'the dataSource bean is the given data source'
        applicationContext.getBean('dataSource', DataSource).is(dataSource)
    }
}
@Entity
class Person {
    Long id
    Long version
    String name

    static constraints = {
        name blank:false
    }
}

@Entity
class Book {
    Long id
    Long version
    String name

    static mapping = {
        datasources( ['books', 'moreBooks'] )
    }
    static constraints = {
        name blank:false
    }
}

@Entity
class Author {
    Long id
    Long version
    String name

    static mapping = {
        datasource 'ALL'
    }
    static constraints = {
        name blank:false
    }
}
