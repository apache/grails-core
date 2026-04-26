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

import grails.gorm.annotation.Entity
import org.grails.orm.hibernate.HibernateDatastore
import org.hibernate.Session
import org.hibernate.SessionFactory
import org.hibernate.dialect.H2Dialect
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.transaction.PlatformTransactionManager
import spock.lang.AutoCleanup
import spock.lang.Specification

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
        println applicationContext.getBeanDefinitionNames()

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
        HibernateDatastore hibernateDatastore = applicationContext.getBean(HibernateDatastore)
        println "Author.moreBooks class is: " + Author.moreBooks.getClass().getName()
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
