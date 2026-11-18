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
package org.grails.datastore.gorm.mongo.connections

import org.springframework.transaction.IllegalTransactionStateException
import spock.lang.AutoCleanup
import spock.lang.Unroll
import spock.util.environment.RestoreSystemProperties

import grails.gorm.MultiTenant
import grails.gorm.annotation.Entity
import grails.gorm.multitenancy.Tenants
import grails.gorm.services.Service
import grails.gorm.transactions.Transactional
import org.apache.grails.testing.mongo.AutoStartedMongoSpec
import org.grails.datastore.gorm.GormEnhancer
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.mongo.MongoDatastore
import org.grails.datastore.mapping.mongo.config.MongoSettings
import org.grails.datastore.mapping.multitenancy.resolvers.SystemPropertyTenantResolver

/**
 * With a database per tenant, a class that is not multi-tenant is unaffected by a tenant block: inside
 * Tenants.withId every way of reaching its data reaches the default database, as it does outside one.
 */
@RestoreSystemProperties
class PlainClassInTenantBlockSpec extends AutoStartedMongoSpec {

    @AutoCleanup
    MongoDatastore datastore

    PlainMongoNote seed

    @Override
    boolean shouldInitializeDatastore() {
        false
    }

    void setup() {
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, '')
        datastore = new MongoDatastore([
                'grails.gorm.multiTenancy.mode'               : 'DATABASE',
                'grails.gorm.multiTenancy.tenantResolverClass': SystemPropertyTenantResolver,
                (MongoSettings.SETTING_URL)                   : "mongodb://${mongoHost}:${mongoPort}/plainClassDefaultDb".toString(),
                (MongoSettings.SETTING_CONNECTIONS)           : [
                        foo: [url: "mongodb://${mongoHost}:${mongoPort}/plainClassFooDb".toString()]
                ]
        ], PlainMongoNote, TenantMongoNote)
        seed = new PlainMongoNote(text: 'seed').save(flush: true)
    }

    void cleanup() {
        datastore?.mongoClient?.listDatabaseNames()?.findAll { String name -> name.startsWith('plainClass') }
                ?.each { String name -> datastore.mongoClient.getDatabase(name).drop() }
    }

    @Unroll
    void '#operation inside a tenant block reaches the default database'() {
        expect:
        Tenants.withId('foo') { check.call(this) }

        where:
        operation                    | check
        'count'                      | { PlainClassInTenantBlockSpec spec -> PlainMongoNote.count() == 1 }
        'list'                       | { PlainClassInTenantBlockSpec spec -> PlainMongoNote.list()*.text == ['seed'] }
        'get'                        | { PlainClassInTenantBlockSpec spec -> PlainMongoNote.get(spec.seed.id)?.text == 'seed' }
        'a where query'              | { PlainClassInTenantBlockSpec spec -> PlainMongoNote.where { text == 'seed' }.count() == 1 }
        'a data service'             | { PlainClassInTenantBlockSpec spec -> spec.datastore.getService(PlainMongoNoteService).count() == 1 }
        'withSession'                | { PlainClassInTenantBlockSpec spec -> PlainMongoNote.withSession { Session s -> s.datastore }.is(spec.datastore) }
        'GormEnhancer.findDatastore' | { PlainClassInTenantBlockSpec spec -> GormEnhancer.findDatastore(PlainMongoNote).is(spec.datastore) }
        'save'                       | { PlainClassInTenantBlockSpec spec -> new PlainMongoNote(text: 'saved').save(flush: true) && spec.documentsIn('plainClassDefaultDb', PlainMongoNote) == 2 && spec.documentsIn('plainClassFooDb', PlainMongoNote) == 0 }
    }

    @Unroll
    void '#write inside a tenant block writes #expected'() {
        when:
        Tenants.withId('foo') { action.call(this) }

        then: 'counted without GORM'
        documentsIn('plainClassDefaultDb', type) == (type == PlainMongoNote ? 1 : 0) + (expected == 'to the default database' ? 1 : 0)
        documentsIn('plainClassFooDb', type) == (expected == 'to the tenant\'s database' ? 1 : 0)

        where:
        write                                                        | type            | expected                    | action
        'a data service save of a plain class'                       | PlainMongoNote  | 'to the default database'   | { PlainClassInTenantBlockSpec spec -> spec.datastore.getService(PlainMongoNoteService).save(new PlainMongoNote(text: 'service')) }
        'a save of a plain class in withTransaction'                 | PlainMongoNote  | 'to the default database'   | { PlainClassInTenantBlockSpec spec -> PlainMongoNote.withTransaction { new PlainMongoNote(text: 'block').save() } }
        'a data service save of a multi-tenant class'                | TenantMongoNote | 'to the tenant\'s database' | { PlainClassInTenantBlockSpec spec -> spec.datastore.getService(TenantMongoNoteService).save(new TenantMongoNote(text: 'service')) }
        'a save of a multi-tenant class in a @Transactional method'  | TenantMongoNote | 'to the tenant\'s database' | { PlainClassInTenantBlockSpec spec -> new TransactionalNoteWriter().saveTenant('transactional') }
        'a save of a multi-tenant class in withTransaction'          | TenantMongoNote | 'to the tenant\'s database' | { PlainClassInTenantBlockSpec spec -> TenantMongoNote.withTransaction { new TenantMongoNote(text: 'block').save() } }
    }

    @Unroll
    void '#write of a plain class inside a tenant\'s transaction fails and writes nothing'() {
        when:
        Tenants.withId('foo') { new TransactionalNoteWriter().run { WRITES[write].call(this) } }

        then: 'read without GORM'
        IllegalTransactionStateException e = thrown()
        e.message.contains(PlainMongoNote.name)
        textsIn('plainClassDefaultDb', PlainMongoNote) == ['seed']
        textsIn('plainClassFooDb', PlainMongoNote) == []

        where:
        write << WRITES.keySet().toList()
    }

    void 'a save of a plain class inside a tenant\'s transaction fails while a session of its datastore is bound, as in a web request'() {
        when:
        PlainMongoNote.withSession {
            Tenants.withId('foo') { new TransactionalNoteWriter().run { new PlainMongoNote(text: 'pending').save() } }
        }

        then:
        thrown(IllegalTransactionStateException)
        textsIn('plainClassDefaultDb', PlainMongoNote) == ['seed']
    }

    @Unroll
    void '#write inside a tenant\'s transaction that #outcome writes the plain class to the default database'() {
        when:
        boolean failed = false
        try {
            Tenants.withId('foo') {
                new TransactionalNoteWriter().run {
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
        textsIn('plainClassDefaultDb', PlainMongoNote) == ['inner', 'seed']
        textsIn('plainClassFooDb', PlainMongoNote) == []

        where:
        [write, fails] << [OWN_TRANSACTION_WRITES.keySet().toList(), [false, true]].combinations()
        outcome = fails ? 'fails' : 'commits'
    }

    static final Map<String, Closure> WRITES = [
            'a save'     : { PlainClassInTenantBlockSpec spec -> new PlainMongoNote(text: 'pending').save() },
            'an insert'  : { PlainClassInTenantBlockSpec spec -> new PlainMongoNote(text: 'pending').insert() },
            'a delete'   : { PlainClassInTenantBlockSpec spec -> PlainMongoNote.get(spec.seed.id).delete() },
            'a saveAll'  : { PlainClassInTenantBlockSpec spec -> PlainMongoNote.saveAll(new PlainMongoNote(text: 'pending')) },
            'a deleteAll': { PlainClassInTenantBlockSpec spec -> PlainMongoNote.deleteAll(PlainMongoNote.get(spec.seed.id)) },
    ]

    static final Map<String, Closure> OWN_TRANSACTION_WRITES = [
            'a save with flush: true'     : { PlainClassInTenantBlockSpec spec -> new PlainMongoNote(text: 'inner').save(flush: true) },
            'a save in withTransaction'   : { PlainClassInTenantBlockSpec spec ->
                PlainMongoNote.withTransaction { new PlainMongoNote(text: 'inner').save() }
            },
            'a save in withNewTransaction': { PlainClassInTenantBlockSpec spec ->
                PlainMongoNote.withNewTransaction { new PlainMongoNote(text: 'inner').save() }
            },
            'a data service save'         : { PlainClassInTenantBlockSpec spec ->
                spec.datastore.getService(PlainMongoNoteService).save(new PlainMongoNote(text: 'inner'))
            },
    ]

    void 'a multi-tenant class still reaches the tenant\'s database inside a tenant block'() {
        given:
        Datastore foo = datastore.getDatastoreForConnection('foo')

        when:
        Tenants.withId('foo') { new TenantMongoNote(text: 'tenant').save(flush: true) }

        then:
        documentsIn('plainClassFooDb', TenantMongoNote) == 1
        documentsIn('plainClassDefaultDb', TenantMongoNote) == 0
        Tenants.withId('foo') { TenantMongoNote.count() } == 1
        Tenants.withId('foo') { GormEnhancer.findDatastore(TenantMongoNote) }.is(foo)
    }

    /**
     * @return the sorted texts of the documents of the class in the database, read without GORM
     */
    List<String> textsIn(String databaseName, Class type) {
        String collectionName = datastore.getCollectionName(datastore.mappingContext.getPersistentEntity(type.name))
        datastore.mongoClient.getDatabase(databaseName).getCollection(collectionName).find()
                .collect { it.getString('text') }.sort()
    }

    /**
     * @return the documents of the class in the database, read without GORM, so that GORM's routing cannot decide it
     */
    long documentsIn(String databaseName, Class type) {
        String collectionName = datastore.getCollectionName(datastore.mappingContext.getPersistentEntity(type.name))
        datastore.mongoClient.getDatabase(databaseName).getCollection(collectionName).countDocuments()
    }
}

@Entity
class PlainMongoNote {

    String text
}

@Entity
class TenantMongoNote implements MultiTenant<TenantMongoNote> {

    String text
}

@Service(PlainMongoNote)
interface PlainMongoNoteService {

    Number count()

    PlainMongoNote save(PlainMongoNote note)
}

@Service(TenantMongoNote)
interface TenantMongoNoteService {

    TenantMongoNote save(TenantMongoNote note)
}

class TransactionalNoteWriter {

    @Transactional
    void run(Closure body) {
        body.call()
    }

    @Transactional
    void saveTenant(String text) {
        new TenantMongoNote(text: text).save()
    }
}
