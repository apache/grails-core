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

import org.hibernate.FetchMode
import org.hibernate.Session
import org.hibernate.dialect.H2Dialect
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity
import grails.gorm.multitenancy.Tenants
import grails.gorm.transactions.Rollback
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.multitenancy.TenantResolver
import org.grails.datastore.mapping.multitenancy.exceptions.TenantNotFoundException
import org.grails.orm.hibernate.HibernateDatastore

@Rollback
class PartitionedMultiTenancyFilterSpec extends Specification {

    @Shared
    @AutoCleanup
    HibernateDatastore datastore

    void setupSpec() {
        datastore = new HibernateDatastore(DatastoreUtils.createPropertyResolver([
                'grails.gorm.multiTenancy.mode'               : MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR,
                'grails.gorm.multiTenancy.tenantResolverClass': FilterSpecTenantResolver,
                'dataSource.url'                              : 'jdbc:h2:mem:filterSpecDB;LOCK_TIMEOUT=10000',
                'dataSource.dbCreate'                         : 'update',
                'dataSource.dialect'                          : H2Dialect.name,
                'hibernate.hbm2ddl.auto'                      : 'create',
        ]), FilteredShelf, FilteredBook, FilteredJoinShelf, FilteredJoinBook,
                FilteredStore, FilteredRack, FilteredRackBook, FilteredGroup, FilteredMember)
    }

    void cleanup() {
        FilterSpecTenantResolver.tenantId = null
        FilterSpecTenantResolver.resolveNull = false
    }

    Session getSession() {
        datastore.sessionFactory.currentSession
    }

    void 'a lazy collection of an entity that is not multi-tenant holds the instances of the current tenant only'() {
        given:
        final shelfId = shelfWithBooks()
        FilterSpecTenantResolver.tenantId = 'alice'

        expect: 'also before any query on a multi-tenant entity has run in the session'
        FilteredShelf.get(shelfId).books*.title == ['alice-1']
    }

    void 'a lazy collection loaded after withoutId holds the instances of the current tenant only'() {
        given:
        final shelfId = shelfWithBooks()
        FilterSpecTenantResolver.tenantId = 'alice'

        when:
        FilteredBook.list()
        Tenants.withoutId(datastore) { FilteredBook.list() }

        then:
        FilteredShelf.get(shelfId).books*.title == ['alice-1']
    }

    void 'inside withoutId a lazy collection holds the instances of every tenant'() {
        given:
        final shelfId = shelfWithBooks()
        FilterSpecTenantResolver.tenantId = 'alice'

        expect:
        Tenants.withoutId(datastore) { FilteredShelf.get(shelfId).books*.title.sort() } == ['alice-1', 'bob-1']
    }

    void 'a lazy collection of a multi-tenant entity cannot be loaded without a current tenant'() {
        given:
        final shelfId = shelfWithBooks()

        when:
        FilteredShelf.get(shelfId).books.size()

        then:
        thrown(TenantNotFoundException)
    }

    void 'a collection that is fetched by join with an instance loaded by id holds the instances of the current tenant only'() {
        given:
        final shelf = new FilteredJoinShelf(name: 'join').save(flush: true)
        Tenants.withId(datastore, 'alice') { new FilteredJoinBook(title: 'alice-1', shelf: shelf).save(flush: true) }
        Tenants.withId(datastore, 'bob') { new FilteredJoinBook(title: 'bob-1', shelf: shelf).save(flush: true) }
        session.clear()
        FilterSpecTenantResolver.tenantId = 'alice'

        expect:
        FilteredJoinShelf.get(shelf.id).books*.title == ['alice-1']
    }

    void 'a query that fetches a collection by join holds the instances of the current tenant only'() {
        given:
        final shelfId = shelfWithBooks()
        FilterSpecTenantResolver.tenantId = 'alice'

        when: 'the criteria query runs first, before any other query has set the filter in the session'
        final criteriaTitles = FilteredShelf.createCriteria().get {
            idEq(shelfId)
            fetchMode('books', FetchMode.JOIN)
        }.books*.title
        session.clear()
        final hql = FilteredShelf.executeQuery(
                'select distinct s from FilteredShelf s join fetch s.books where s.id = :id', [id: shelfId])[0]

        then:
        criteriaTitles == ['alice-1']
        hql.books*.title == ['alice-1']
    }

    void 'a query that fetches a collection by join through another association holds the instances of the current tenant only'() {
        given:
        final rack = new FilteredRack(name: 'rack').save(flush: true)
        final storeId = new FilteredStore(name: 'store', rack: rack).save(flush: true).id
        Tenants.withId(datastore, 'alice') { new FilteredRackBook(title: 'alice-1', rack: rack).save(flush: true) }
        Tenants.withId(datastore, 'bob') { new FilteredRackBook(title: 'bob-1', rack: rack).save(flush: true) }
        session.clear()
        FilterSpecTenantResolver.tenantId = 'alice'

        when: 'the criteria query runs first, before any other query has set the filter in the session'
        final criteriaTitles = FilteredStore.createCriteria().get {
            idEq(storeId)
            fetchMode('rack', FetchMode.JOIN)
            fetchMode('rack.books', FetchMode.JOIN)
        }.rack.books*.title
        session.clear()
        final hql = FilteredStore.executeQuery(
                'select distinct s from FilteredStore s join fetch s.rack r join fetch r.books where s.id = :id', [id: storeId])[0]

        then:
        criteriaTitles == ['alice-1']
        hql.rack.books*.title == ['alice-1']
    }

    void 'a many-to-many collection holds the instances of the current tenant only'() {
        given:
        final group = new FilteredGroup(name: 'group').save(flush: true)
        Tenants.withId(datastore, 'alice') {
            group.addToMembers(new FilteredMember(name: 'alice-1').save(flush: true)).save(flush: true)
        }
        Tenants.withId(datastore, 'bob') {
            group.addToMembers(new FilteredMember(name: 'bob-1').save(flush: true)).save(flush: true)
        }
        session.clear()
        FilterSpecTenantResolver.tenantId = 'alice'

        when:
        final lazy = FilteredGroup.get(group.id).members*.name
        session.clear()
        final fetched = FilteredGroup.executeQuery(
                'select distinct g from FilteredGroup g join fetch g.members where g.id = :id', [id: group.id])[0]

        then:
        lazy == ['alice-1']
        fetched.members*.name == ['alice-1']
    }

    void 'load and proxy return an instance of the current tenant only'() {
        given:
        shelfWithBooks()
        final aliceBookId = Tenants.withoutId(datastore) { FilteredBook.findByTitle('alice-1').id }
        final bobBookId = Tenants.withoutId(datastore) { FilteredBook.findByTitle('bob-1').id }
        session.clear()
        FilterSpecTenantResolver.tenantId = 'alice'

        expect:
        FilteredBook.load(aliceBookId).title == 'alice-1'
        FilteredBook.load(bobBookId) == null
        FilteredBook.proxy(bobBookId) == null
    }

    void 'a resolver that returns null fails a query and a save'() {
        given:
        shelfWithBooks()
        FilterSpecTenantResolver.resolveNull = true

        when:
        FilteredBook.list()

        then:
        thrown(TenantNotFoundException)

        when:
        new FilteredBook(title: 'null-1', tenantId: 'alice', shelf: FilteredShelf.list()[0]).save(flush: true)

        then:
        thrown(TenantNotFoundException)
    }

    private Long shelfWithBooks() {
        final shelf = new FilteredShelf(name: 'lazy').save(flush: true)
        Tenants.withId(datastore, 'alice') { new FilteredBook(title: 'alice-1', shelf: shelf).save(flush: true) }
        Tenants.withId(datastore, 'bob') { new FilteredBook(title: 'bob-1', shelf: shelf).save(flush: true) }
        session.clear()
        shelf.id
    }
}

class FilterSpecTenantResolver implements TenantResolver {

    static Serializable tenantId
    static boolean resolveNull

    @Override
    Serializable resolveTenantIdentifier() {
        if (resolveNull) {
            return null
        }
        if (tenantId == null) {
            throw new TenantNotFoundException()
        }
        tenantId
    }
}

@Entity
class FilteredShelf {

    String name

    static hasMany = [books: FilteredBook]
}

@Entity
class FilteredBook implements MultiTenant<FilteredBook> {

    String title
    String tenantId

    static belongsTo = [shelf: FilteredShelf]
}

@Entity
class FilteredJoinShelf {

    String name

    static hasMany = [books: FilteredJoinBook]

    static mapping = {
        books fetch: 'join', lazy: false
    }
}

@Entity
class FilteredJoinBook implements MultiTenant<FilteredJoinBook> {

    String title
    String tenantId

    static belongsTo = [shelf: FilteredJoinShelf]
}

@Entity
class FilteredStore {

    String name
    FilteredRack rack
}

@Entity
class FilteredRack {

    String name

    static hasMany = [books: FilteredRackBook]
}

@Entity
class FilteredRackBook implements MultiTenant<FilteredRackBook> {

    String title
    String tenantId

    static belongsTo = [rack: FilteredRack]
}

@Entity
class FilteredGroup {

    String name

    static hasMany = [members: FilteredMember]
}

@Entity
class FilteredMember implements MultiTenant<FilteredMember> {

    String name
    String tenantId

    static hasMany = [groups: FilteredGroup]
    static belongsTo = FilteredGroup
}
