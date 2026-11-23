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

import org.springframework.transaction.IllegalTransactionStateException
import org.springframework.transaction.support.AbstractPlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
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
                schema ? [ConnectionSource.DEFAULT] : [ConnectionSource.DEFAULT, 'foo', 'bar'],
                TenantNote, PlainNote
        )
        if (schema) {
            datastore.addTenantForSchema('foo')
            datastore.addTenantForSchema('bar')
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
    void 'with #mode multi-tenancy and synchronization=#synchronization, #write of a plain class inside a tenant\'s transaction fails and writes nothing'() {
        given:
        startDatastore(mode)
        ((SimpleMapDatastore) datastore.getDatastoreForConnection('foo')).transactionManager.transactionSynchronization = synchronization

        when:
        Tenants.withId('foo') { new TransactionalPlainNoteWriter().run { WRITES[write].call(this) } }

        then:
        IllegalTransactionStateException e = thrown()
        e.message.contains(PlainNote.name)
        e.message.contains('PlainNote.withTransaction { }')
        e.message.contains('Flushing does not replace a transaction.')
        PlainNote.withTransaction { PlainNote.list()*.text } == ['seed']

        where:
        [mode, write, synchronization] << [MODES, WRITES.keySet().toList(), SYNCHRONIZATION_MODES].combinations()
    }

    @Unroll
    void 'with #mode multi-tenancy and synchronization=#synchronization, a save with flush=#flush fails while a session of its datastore is bound, as in a web request'() {
        given:
        startDatastore(mode)
        ((SimpleMapDatastore) datastore.getDatastoreForConnection('foo')).transactionManager.transactionSynchronization = synchronization

        when:
        PlainNote.withSession {
            Tenants.withId('foo') {
                new TransactionalPlainNoteWriter().run {
                    new PlainNote(text: 'inner').save(flush: flush)
                    throw new IllegalStateException('tenant failed')
                }
            }
        }

        then:
        thrown(IllegalTransactionStateException)
        PlainNote.withTransaction { PlainNote.list()*.text } == ['seed']

        where:
        [mode, flush, synchronization] << [MODES, [false, true], SYNCHRONIZATION_MODES].combinations()
    }

    @Unroll
    void 'with #mode multi-tenancy and synchronization=#synchronization, #write inside a tenant\'s transaction that #outcome writes the plain class'() {
        given:
        startDatastore(mode)
        ((SimpleMapDatastore) datastore.getDatastoreForConnection('foo')).transactionManager.transactionSynchronization = synchronization

        when:
        boolean failed = false
        try {
            Tenants.withId('foo') {
                new TransactionalPlainNoteWriter().run {
                    OWN_TRANSACTION_WRITES[write].call(this)
                    if (fails) {
                        throw new IllegalStateException('rolled back')
                    }
                }
            }
        }
        catch (IllegalStateException ignored) {
            failed = true
        }

        then: 'the write is the plain class\'s own, so it stays whatever the tenant\'s transaction does'
        failed == fails
        PlainNote.withTransaction { PlainNote.list()*.text.sort() } == ['inner', 'seed']

        where:
        [mode, write, fails, synchronization] << [MODES, OWN_TRANSACTION_WRITES.keySet().toList(), [false, true], SYNCHRONIZATION_MODES].combinations()
        outcome = fails ? 'fails' : 'commits'
    }

    @Unroll
    void 'with #mode multi-tenancy and synchronization=#synchronization, a multi-tenant class is still written by the tenant\'s transaction'() {
        given:
        startDatastore(mode)
        ((SimpleMapDatastore) datastore.getDatastoreForConnection('foo')).transactionManager.transactionSynchronization = synchronization

        when:
        Tenants.withId('foo') { new TransactionalPlainNoteWriter().run { new TenantNote(text: 'tenant').save() } }

        then:
        Tenants.withId('foo') { TenantNote.withTransaction { TenantNote.list()*.text } } == ['tenant']

        where:
        [mode, synchronization] << [MODES, SYNCHRONIZATION_MODES].combinations()
    }

    @Unroll
    void 'with #mode multi-tenancy, a save of a plain class inside a tenant block with no transaction running does not fail'() {
        given:
        startDatastore(mode)

        when:
        Tenants.withId('foo') { new PlainNote(text: 'unflushed').save() }

        then:
        notThrown(IllegalTransactionStateException)

        where:
        mode << MODES
    }

    @Unroll
    void 'with #mode multi-tenancy, a flushed save with tenant #tenantId and boundSession=#boundSession needs no tenant transaction'() {
        given:
        startDatastore(mode)
        PlainNote saved
        Closure write = {
            Tenants.withTenant(PlainNote, tenantId) {
                saved = new PlainNote(text: 'flushed').save(flush: true)
            }
        }

        when:
        if (boundSession) {
            PlainNote.withSession { write.call() }
        }
        else {
            write.call()
        }

        then:
        holder(PlainNote, saved.id).is(datastore)
        PlainNote.withTransaction { PlainNote.list()*.text.sort() } == ['flushed', 'seed']

        where:
        [mode, tenantId, boundSession] << [MODES, ['foo', 'unregistered'], [false, true]].combinations()
    }

    @Unroll
    void 'with #mode multi-tenancy and synchronization=#synchronization, a flushed save with tenant #tenantId ignores another transaction and boundSession=#boundSession'() {
        given:
        startDatastore(mode)
        def other = (SimpleMapDatastore) datastore.getDatastoreForConnection('bar')
        other.transactionManager.transactionSynchronization = synchronization
        Closure write = {
            Tenants.withTenant(PlainNote, tenantId) {
                new PlainNote(text: 'flushed').save(flush: true)
            }
        }

        when:
        new TransactionTemplate(other.transactionManager).execute { status ->
            if (boundSession) {
                PlainNote.withSession { write.call() }
            }
            else {
                write.call()
            }
            status.setRollbackOnly()
        }

        then:
        PlainNote.withTransaction { PlainNote.list()*.text.sort() } == ['flushed', 'seed']

        where:
        [mode, tenantId, boundSession, synchronization] << [MODES, ['foo', 'unregistered'], [false, true], SYNCHRONIZATION_MODES].combinations()
    }

    @Unroll
    void 'with #mode multi-tenancy and synchronization=#synchronization, an inactive tenant session allows a flushed save with unrelatedTransaction=#unrelatedTransaction'() {
        given:
        startDatastore(mode)
        def other = (SimpleMapDatastore) datastore.getDatastoreForConnection('bar')
        other.transactionManager.transactionSynchronization = synchronization
        Closure write = {
            Tenants.withId('foo') {
                TenantNote.withSession {
                    new PlainNote(text: 'flushed').save(flush: true)
                }
            }
        }

        when:
        if (unrelatedTransaction) {
            new TransactionTemplate(other.transactionManager).execute { status ->
                write.call()
                status.setRollbackOnly()
            }
        }
        else {
            write.call()
        }

        then:
        PlainNote.withTransaction { PlainNote.list()*.text.sort() } == ['flushed', 'seed']

        where:
        [mode, synchronization, unrelatedTransaction] << [MODES, SYNCHRONIZATION_MODES, [false, true]].combinations()
    }

    static final List<Integer> SYNCHRONIZATION_MODES = [
            AbstractPlatformTransactionManager.SYNCHRONIZATION_ALWAYS,
            AbstractPlatformTransactionManager.SYNCHRONIZATION_NEVER
    ]

    static final List<MultiTenancySettings.MultiTenancyMode> MODES = [
            MultiTenancySettings.MultiTenancyMode.DATABASE, MultiTenancySettings.MultiTenancyMode.SCHEMA
    ]

    static final Map<String, Closure> WRITES = [
            'a flushed save': { PlainClassInTenantBlockSpec spec -> new PlainNote(text: 'inner').save(flush: true) },
            'a flushed insert': { PlainClassInTenantBlockSpec spec -> new PlainNote(text: 'inner').insert(flush: true) },
            'a flushed delete': { PlainClassInTenantBlockSpec spec -> PlainNote.get(spec.seed.id).delete(flush: true) },
            'a flushed deleteAll': { PlainClassInTenantBlockSpec spec -> PlainNote.deleteAll([flush: true], [PlainNote.get(spec.seed.id)]) },
            'a save'     : { PlainClassInTenantBlockSpec spec -> new PlainNote(text: 'pending').save() },
            'an insert'  : { PlainClassInTenantBlockSpec spec -> new PlainNote(text: 'pending').insert() },
            'a delete'   : { PlainClassInTenantBlockSpec spec -> PlainNote.get(spec.seed.id).delete() },
            'a saveAll'  : { PlainClassInTenantBlockSpec spec -> PlainNote.saveAll(new PlainNote(text: 'pending')) },
            'a deleteAll': { PlainClassInTenantBlockSpec spec -> PlainNote.deleteAll(PlainNote.get(spec.seed.id)) },
            'an iterable saveAll': { PlainClassInTenantBlockSpec spec -> PlainNote.saveAll([new PlainNote(text: 'pending')]) },
            'an iterable deleteAll': { PlainClassInTenantBlockSpec spec -> PlainNote.deleteAll([PlainNote.get(spec.seed.id)]) },
            'an unflushed iterable deleteAll': { PlainClassInTenantBlockSpec spec -> PlainNote.deleteAll([flush: false], [PlainNote.get(spec.seed.id)]) },
    ]

    static final Map<String, Closure> OWN_TRANSACTION_WRITES = [
            'a flushed save in withTransaction': { PlainClassInTenantBlockSpec spec ->
                PlainNote.withTransaction { new PlainNote(text: 'inner').save(flush: true) }
            },
            'a save in withTransaction'   : { PlainClassInTenantBlockSpec spec ->
                PlainNote.withTransaction { new PlainNote(text: 'inner').save() }
            },
            'a save in withNewTransaction': { PlainClassInTenantBlockSpec spec ->
                PlainNote.withNewTransaction { new PlainNote(text: 'inner').save() }
            },
            'a data service save'         : { PlainClassInTenantBlockSpec spec ->
                spec.datastore.getService(PlainNoteService).save(new PlainNote(text: 'inner'))
            },
    ]

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
    void run(Closure body) {
        body.call()
    }
}
