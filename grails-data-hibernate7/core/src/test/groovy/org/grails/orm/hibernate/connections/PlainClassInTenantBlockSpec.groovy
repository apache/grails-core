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

import groovy.sql.Sql

import org.hibernate.dialect.H2Dialect
import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.lang.Unroll
import spock.util.environment.RestoreSystemProperties

import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity
import grails.gorm.multitenancy.Tenants
import grails.gorm.services.Service
import org.grails.datastore.gorm.GormEnhancer
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.multitenancy.resolvers.SystemPropertyTenantResolver
import org.grails.orm.hibernate.HibernateDatastore

/**
 * With a database per tenant, a class that is not multi-tenant is unaffected by a tenant block: inside
 * Tenants.withId every way of reaching its data reaches the default database, as it does outside one.
 */
@RestoreSystemProperties
class PlainClassInTenantBlockSpec extends Specification {

    @AutoCleanup
    HibernateDatastore datastore

    PlainHibernateNote seed

    void setup() {
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, '')
        datastore = new HibernateDatastore(DatastoreUtils.createPropertyResolver([
                'grails.gorm.multiTenancy.mode'               : 'DATABASE',
                'grails.gorm.multiTenancy.tenantResolverClass': SystemPropertyTenantResolver,
                'dataSource.url'                              : 'jdbc:h2:mem:plainClassDefaultDB;LOCK_TIMEOUT=10000',
                'dataSource.dbCreate'                         : 'create-drop',
                'dataSource.dialect'                          : H2Dialect.name,
                'hibernate.hbm2ddl.auto'                      : 'create',
                'dataSources.foo'                             : [url: 'jdbc:h2:mem:plainClassFooDB;LOCK_TIMEOUT=10000']
        ]), PlainHibernateNote, TenantHibernateNote)
        seed = PlainHibernateNote.withTransaction { new PlainHibernateNote(text: 'seed').save(flush: true) }
    }

    @Unroll
    void '#operation inside a tenant block reaches the default database'() {
        expect:
        Tenants.withId('foo') { PlainHibernateNote.withTransaction { check.call(this) } }

        where:
        operation                    | check
        'count'                      | { PlainClassInTenantBlockSpec spec -> PlainHibernateNote.count() == 1 }
        'list'                       | { PlainClassInTenantBlockSpec spec -> PlainHibernateNote.list()*.text == ['seed'] }
        'get'                        | { PlainClassInTenantBlockSpec spec -> PlainHibernateNote.get(spec.seed.id)?.text == 'seed' }
        'a where query'              | { PlainClassInTenantBlockSpec spec -> PlainHibernateNote.where { text == 'seed' }.count() == 1 }
        'a data service'             | { PlainClassInTenantBlockSpec spec -> spec.datastore.getService(PlainHibernateNoteService).count() == 1 }
        'withSession'                | { PlainClassInTenantBlockSpec spec -> PlainHibernateNote.withSession { org.hibernate.Session s -> s.sessionFactory }.is(spec.datastore.sessionFactory) }
        'GormEnhancer.findDatastore' | { PlainClassInTenantBlockSpec spec -> GormEnhancer.findDatastore(PlainHibernateNote).is(spec.datastore) }
    }

    void 'save inside a tenant block writes to the default database'() {
        when:
        Tenants.withId('foo') {
            PlainHibernateNote.withTransaction { new PlainHibernateNote(text: 'saved').save(flush: true) }
        }

        then:
        PlainHibernateNote.withNewTransaction { PlainHibernateNote.list()*.text.sort() } == ['saved', 'seed']
    }

    @Unroll
    void '#write of a plain class inside a tenant block writes to the default database'() {
        when:
        Tenants.withId('foo') { action.call(this) }

        then:
        PlainHibernateNote.withNewTransaction { PlainHibernateNote.list()*.text.sort() } == ['saved', 'seed']

        where:
        write                          | action
        'a data service save'          | { PlainClassInTenantBlockSpec spec -> spec.datastore.getService(PlainHibernateNoteService).save(new PlainHibernateNote(text: 'saved')) }
        'a save in its withTransaction' | { PlainClassInTenantBlockSpec spec -> PlainHibernateNote.withTransaction { new PlainHibernateNote(text: 'saved').save() } }
    }

    void 'a multi-tenant class still reaches the tenant\'s database inside a tenant block'() {
        given:
        Datastore foo = datastore.getDatastoreForConnection('foo')

        when:
        Tenants.withId('foo') { TenantHibernateNote.withTransaction { new TenantHibernateNote(text: 'tenant').save(flush: true) } }

        then:
        Tenants.withId('foo') { TenantHibernateNote.withTransaction { TenantHibernateNote.count() } } == 1
        rowsInDefaultDatabase('tenant_hibernate_note') == 0
        Tenants.withId('foo') { GormEnhancer.findDatastore(TenantHibernateNote) }.is(foo)
    }

    /**
     * @return the rows of the table in the default database, read with JDBC, so that GORM's routing cannot decide it
     */
    long rowsInDefaultDatabase(String table) {
        new Sql(datastore.dataSource).withCloseable { Sql sql -> sql.firstRow('select count(*) as c from ' + table).c as long }
    }
}

@Entity
class PlainHibernateNote {

    String text
}

@Entity
class TenantHibernateNote implements MultiTenant<TenantHibernateNote> {

    String text
}

@Service(PlainHibernateNote)
interface PlainHibernateNoteService {

    Number count()

    PlainHibernateNote save(PlainHibernateNote note)
}

