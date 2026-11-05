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
package grails.gorm.tests.multitenancy

import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.multitenancy.resolvers.SystemPropertyTenantResolver
import org.grails.orm.hibernate.HibernateDatastore
import org.hibernate.Hibernate
import org.hibernate.dialect.H2Dialect
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import jakarta.persistence.criteria.JoinType

/**
 * A join type requested for a collection must apply the tenant filter of the elements of the collection, as a join does.
 */
class MultiTenancyRequestedJoinSpec extends Specification {

    @Shared @AutoCleanup HibernateDatastore datastore

    void setupSpec() {
        Map config = [
                'grails.gorm.multiTenancy.mode'               : MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR,
                'grails.gorm.multiTenancy.tenantResolverClass': SystemPropertyTenantResolver.name,
                'dataSource.url'                              : 'jdbc:h2:mem:multiTenancyRequestedJoin;LOCK_TIMEOUT=10000',
                'dataSource.dialect'                          : H2Dialect.name,
                'hibernate.flush.mode'                        : 'COMMIT',
                'hibernate.hbm2ddl.auto'                      : 'create',
        ]
        datastore = new HibernateDatastore(DatastoreUtils.createPropertyResolver(config), RequestedJoinShelf, RequestedJoinBook)

        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'fiction')
        RequestedJoinShelf.withTransaction {
            final shared = new RequestedJoinShelf(name: 'Shared shelf').save(failOnError: true)
            new RequestedJoinBook(title: 'A novel', shelf: shared).save(failOnError: true)
            new RequestedJoinShelf(name: 'Travel shelf').save(failOnError: true, flush: true)
        }
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'travel')
        RequestedJoinShelf.withTransaction {
            new RequestedJoinBook(title: 'A guide', shelf: RequestedJoinShelf.findByName('Travel shelf')).save(failOnError: true)
            new RequestedJoinBook(title: 'A map', shelf: RequestedJoinShelf.findByName('Shared shelf')).save(failOnError: true, flush: true)
        }
    }

    void setup() {
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, 'fiction')
    }

    void cleanup() {
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, '')
    }

    void 'a where query that requests #joinType for a collection leaves out an instance whose elements belong to another tenant'() {
        when:
        final List<RequestedJoinShelf> results = RequestedJoinShelf.withTransaction {
            final shelves = RequestedJoinShelf.where {
                name ==~ '%shelf'
            }.list(arguments + [fetch: [books: joinType]])
            shelves.each { Hibernate.initialize(it.books) }
            shelves
        }

        then:
        results*.name == ['Shared shelf']

        and: 'the collection holds the books of the current tenant only'
        results.first().books*.title == ['A novel']

        where:
        joinType       | arguments
        JoinType.INNER | [:]
        JoinType.RIGHT | [:]
        JoinType.INNER | [sort: 'books.title']
        JoinType.INNER | [max: 10]
    }

    void 'count applies the tenant filter to an inner join that is requested for a collection'() {
        when:
        final count = RequestedJoinShelf.withTransaction {
            RequestedJoinShelf.where {
                name ==~ '%shelf'
            }.join('books', JoinType.INNER).count()
        }

        then:
        count == 1
    }

    void 'a criteria query that requests an inner join for a collection leaves out an instance whose elements belong to another tenant'() {
        when:
        final List<RequestedJoinShelf> results = RequestedJoinShelf.withTransaction {
            RequestedJoinShelf.createCriteria().list {
                like('name', '%shelf')
                join('books', JoinType.INNER)
            }
        }

        then:
        results*.name == ['Shared shelf']
    }

    void 'a left join requested for a collection returns an instance whose elements belong to another tenant'() {
        when:
        final List<RequestedJoinShelf> results = RequestedJoinShelf.withTransaction {
            RequestedJoinShelf.where {
                name ==~ '%shelf'
            }.list(fetch: [books: JoinType.LEFT], sort: 'name')
        }

        then:
        results*.name == ['Shared shelf', 'Travel shelf']
    }
}

@Entity
class RequestedJoinShelf {
    String name
    static hasMany = [books: RequestedJoinBook]
}

@Entity
class RequestedJoinBook implements MultiTenant<RequestedJoinBook> {
    String title
    String tenantId
    static belongsTo = [shelf: RequestedJoinShelf]
}
