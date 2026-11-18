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
class PartitionedMultiTenancyResolverQuerySpec extends Specification {

    @Shared
    @AutoCleanup
    HibernateDatastore datastore

    void setupSpec() {
        datastore = new HibernateDatastore(DatastoreUtils.createPropertyResolver([
                'grails.gorm.multiTenancy.mode'               : MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR,
                'grails.gorm.multiTenancy.tenantResolverClass': QueryingTenantResolver,
                'dataSource.url'                              : 'jdbc:h2:mem:resolverQuerySpecDB;LOCK_TIMEOUT=10000',
                'dataSource.dbCreate'                         : 'update',
                'dataSource.dialect'                          : H2Dialect.name,
                'hibernate.hbm2ddl.auto'                      : 'create',
        ]), ResolverAccount, ResolverDocument, ResolverNote)
    }

    void cleanup() {
        QueryingTenantResolver.host = null
        QueryingTenantResolver.failure = null
        QueryingTenantResolver.nested = false
    }

    void 'a tenant resolver can query an entity that has a collection of a multi-tenant entity'() {
        given:
        accountWithDocuments()
        QueryingTenantResolver.host = 'alice.example.com'

        expect:
        ResolverDocument.list()*.title == ['alice-1']
        !QueryingTenantResolver.nested
    }

    void 'a query on an entity that has a collection of a multi-tenant entity resolves the tenant without nesting'() {
        given:
        final accountId = accountWithDocuments()
        QueryingTenantResolver.host = 'alice.example.com'

        when:
        final account = ResolverAccount.executeQuery(
                'select distinct a from ResolverAccount a join fetch a.documents where a.id = :id', [id: accountId])[0]

        then:
        account.documents*.title == ['alice-1']
        !QueryingTenantResolver.nested
    }

    void 'a criteria query on an entity that has a collection of a multi-tenant entity resolves the tenant without nesting'() {
        given:
        final accountId = accountWithDocuments()
        QueryingTenantResolver.host = 'alice.example.com'

        when:
        final account = ResolverAccount.createCriteria().get {
            idEq(accountId)
            fetchMode('documents', FetchMode.JOIN)
        }

        then:
        account.documents*.title == ['alice-1']
        !QueryingTenantResolver.nested
    }

    void 'a tenant resolver that fails fails a query on an entity that has a collection of a multi-tenant entity'() {
        given:
        accountWithDocuments()
        QueryingTenantResolver.failure = new IllegalStateException('No principal')

        when:
        ResolverAccount.list()

        then:
        thrown(IllegalStateException)
    }

    void 'a query on an entity without a collection of a multi-tenant entity does not call the tenant resolver'() {
        given:
        new ResolverNote(text: 'note').save(flush: true)
        QueryingTenantResolver.failure = new IllegalStateException('No principal')

        expect:
        ResolverNote.list()*.text == ['note']
        ResolverNote.createCriteria().list { eq('text', 'note') }*.text == ['note']
    }

    private Long accountWithDocuments() {
        final account = new ResolverAccount(host: 'alice.example.com', tenantKey: 'alice').save(flush: true)
        Tenants.withId(datastore, 'alice') { new ResolverDocument(title: 'alice-1', account: account).save(flush: true) }
        Tenants.withId(datastore, 'bob') { new ResolverDocument(title: 'bob-1', account: account).save(flush: true) }
        datastore.sessionFactory.currentSession.clear()
        account.id
    }
}

class QueryingTenantResolver implements TenantResolver {

    static String host
    static RuntimeException failure
    static boolean nested
    private static int depth

    @Override
    Serializable resolveTenantIdentifier() throws TenantNotFoundException {
        if (failure != null) {
            throw failure
        }
        if (host == null) {
            throw new TenantNotFoundException('No host')
        }
        nested = nested || depth > 0
        depth++
        try {
            ResolverAccount.findByHost(host)?.tenantKey
        }
        finally {
            depth--
        }
    }
}

@Entity
class ResolverAccount {

    String host
    String tenantKey

    static hasMany = [documents: ResolverDocument]
}

@Entity
class ResolverDocument implements MultiTenant<ResolverDocument> {

    String title
    String tenantId

    static belongsTo = [account: ResolverAccount]
}

@Entity
class ResolverNote {

    String text
}
