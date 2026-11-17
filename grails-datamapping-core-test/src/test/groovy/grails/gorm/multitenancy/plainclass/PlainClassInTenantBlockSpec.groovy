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
package grails.gorm.multitenancy.plainclass

import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.lang.Unroll
import spock.util.environment.RestoreSystemProperties

import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity
import grails.gorm.multitenancy.Tenants
import grails.gorm.services.Service
import grails.gorm.transactions.Transactional
import org.grails.datastore.gorm.GormEnhancer
import org.grails.datastore.mapping.config.Settings
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.core.SessionCallback
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.multitenancy.resolvers.SystemPropertyTenantResolver
import org.grails.datastore.mapping.simple.SimpleMapDatastore

/**
 * A class that is not multi-tenant is unaffected by a tenant block: inside Tenants.withId every way of reaching its
 * data reaches the datastore it reaches outside one.
 */
@RestoreSystemProperties
class PlainClassInTenantBlockSpec extends Specification {

    @AutoCleanup
    SimpleMapDatastore datastore

    PlainNote seed

    private void startDatastore(MultiTenancySettings.MultiTenancyMode mode) {
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, '')
        boolean schema = mode == MultiTenancySettings.MultiTenancyMode.SCHEMA
        datastore = new SimpleMapDatastore(
                DatastoreUtils.createPropertyResolver([
                        (Settings.SETTING_MULTI_TENANCY_MODE)   : mode,
                        (Settings.SETTING_MULTI_TENANT_RESOLVER): new SystemPropertyTenantResolver()
                ]),
                schema ? [ConnectionSource.DEFAULT] : [ConnectionSource.DEFAULT, 'foo'],
                TenantNote, PlainNote
        )
        if (schema) {
            datastore.addTenantForSchema('foo')
        }
        seed = PlainNote.withTransaction { new PlainNote(text: 'seed').save(flush: true) }
    }

    @Unroll
    void 'with #mode multi-tenancy, #operation inside a tenant block reaches the default datastore'() {
        given:
        startDatastore(mode)

        expect:
        Tenants.withId('foo') { check.call(this) }

        where:
        [mode, operation, check] << [
                [MultiTenancySettings.MultiTenancyMode.DATABASE, MultiTenancySettings.MultiTenancyMode.SCHEMA],
                OPERATIONS.entrySet().toList()
        ].combinations().collect { m, e -> [m, e.key, e.value] }
    }

    @Unroll
    void 'with #mode multi-tenancy, a multi-tenant class still reaches the tenant\'s datastore inside a tenant block'() {
        given:
        startDatastore(mode)
        Datastore foo = datastore.getDatastoreForConnection('foo')

        when:
        TenantNote saved = Tenants.withId('foo') { TenantNote.withTransaction { new TenantNote(text: 'tenant').save(flush: true) } }

        then:
        holder(TenantNote, saved.id).is(foo)
        Tenants.withId('foo') { TenantNote.count() } == 1
        Tenants.withId('foo') { TenantNote.withSession { Session s -> s.datastore } }.is(foo)
        Tenants.withId('foo') { GormEnhancer.findDatastore(TenantNote) }.is(foo)

        where:
        mode << [MultiTenancySettings.MultiTenancyMode.DATABASE, MultiTenancySettings.MultiTenancyMode.SCHEMA]
    }

    @Unroll
    void 'with #mode multi-tenancy, a @Transactional method that saves a plain class inside a tenant block and then fails writes nothing'() {
        given:
        startDatastore(mode)

        when:
        Tenants.withId('foo') { new TransactionalPlainNoteWriter().saveAndFail('rolled back') }

        then:
        thrown(IllegalStateException)
        PlainNote.withTransaction { PlainNote.list()*.text } == ['seed']

        where:
        mode << [MultiTenancySettings.MultiTenancyMode.DATABASE, MultiTenancySettings.MultiTenancyMode.SCHEMA]
    }

    @Unroll
    void 'with #mode multi-tenancy, a plain class flushed in a @Transactional method inside a tenant block stays when the method then fails'() {
        given:
        startDatastore(mode)

        when: 'the flush writes to the default datastore at once, outside the tenant\'s transaction'
        Tenants.withId('foo') { new TransactionalPlainNoteWriter().flushAndFail('flushed') }

        then:
        thrown(IllegalStateException)
        PlainNote.withTransaction { PlainNote.list()*.text } == ['seed', 'flushed']

        where:
        mode << [MultiTenancySettings.MultiTenancyMode.DATABASE, MultiTenancySettings.MultiTenancyMode.SCHEMA]
    }

    static final Map<String, Closure<Boolean>> OPERATIONS = [
            'count'                     : { PlainClassInTenantBlockSpec spec -> PlainNote.count() == 1 },
            'list'                      : { PlainClassInTenantBlockSpec spec -> PlainNote.list()*.text == ['seed'] },
            'get'                       : { PlainClassInTenantBlockSpec spec -> PlainNote.get(spec.seed.id)?.text == 'seed' },
            'a where query'             : { PlainClassInTenantBlockSpec spec -> PlainNote.where { text == 'seed' }.count() == 1 },
            'a data service'            : { PlainClassInTenantBlockSpec spec -> spec.datastore.getService(PlainNoteService).count() == 1 },
            'withSession'               : { PlainClassInTenantBlockSpec spec -> PlainNote.withSession { Session s -> s.datastore }.is(spec.datastore) },
            'GormEnhancer.findDatastore': { PlainClassInTenantBlockSpec spec -> GormEnhancer.findDatastore(PlainNote).is(spec.datastore) },
            'a data service save'       : { PlainClassInTenantBlockSpec spec ->
                PlainNote saved = spec.datastore.getService(PlainNoteService).save(new PlainNote(text: 'service'))
                spec.holder(PlainNote, saved.id).is(spec.datastore)
            },
            'a @Transactional save'     : { PlainClassInTenantBlockSpec spec ->
                PlainNote saved = new TransactionalPlainNoteWriter().save('transactional')
                spec.holder(PlainNote, saved.id).is(spec.datastore)
            },
            'save'                      : { PlainClassInTenantBlockSpec spec ->
                PlainNote saved = PlainNote.withTransaction { new PlainNote(text: 'saved').save(flush: true) }
                spec.holder(PlainNote, saved.id).is(spec.datastore)
            },
    ]

    Datastore holder(Class type, Serializable id) {
        List<Datastore> holding = [datastore, datastore.getDatastoreForConnection('foo')].findAll { Datastore ds ->
            DatastoreUtils.execute(ds, { Session session -> session.retrieve(type, id) } as SessionCallback) != null
        }
        holding.size() == 1 ? holding.first() : null
    }
}

@Entity
class PlainNote {

    String text
}

@Entity
class TenantNote implements MultiTenant<TenantNote> {

    String text
}

@Service(PlainNote)
interface PlainNoteService {

    Number count()

    PlainNote save(PlainNote note)
}

class TransactionalPlainNoteWriter {

    @Transactional
    PlainNote save(String text) {
        new PlainNote(text: text).save()
    }

    @Transactional
    void saveAndFail(String text) {
        new PlainNote(text: text).save()
        throw new IllegalStateException('rolled back')
    }

    @Transactional
    void flushAndFail(String text) {
        new PlainNote(text: text).save(flush: true)
        throw new IllegalStateException('rolled back')
    }
}
