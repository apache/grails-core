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

import java.util.function.Supplier

import spock.lang.AutoCleanup

import org.springframework.context.support.GenericApplicationContext

import grails.gorm.annotation.CreatedBy
import grails.gorm.annotation.Entity
import grails.gorm.annotation.LastModifiedBy
import org.apache.grails.testing.mongo.AutoStartedMongoSpec
import org.grails.datastore.gorm.events.ConfigurableApplicationContextEventPublisher
import org.grails.datastore.gorm.timestamp.AuditorAware
import org.grails.datastore.mapping.config.Settings
import org.grails.datastore.mapping.mongo.MongoDatastore

/**
 * The listener of every connection, not only that of the default one, sets the auditor properties from the
 * application context of the datastore's event publisher, follows the auto-timestamp settings of the configuration,
 * and is the one a domain class answers with.
 */
class AutoTimestampEventListenerConnectionsSpec extends AutoStartedMongoSpec {

    private static final Date PAST = new Date(110_000_000_000L)

    @AutoCleanup
    GenericApplicationContext context

    @AutoCleanup
    MongoDatastore datastore

    @Override
    boolean shouldInitializeDatastore() {
        false
    }

    void cleanup() {
        datastore?.mongoClient?.listDatabaseNames()?.findAll { String name -> name.startsWith('timestampConnections') }
                ?.each { String name -> datastore.mongoClient.getDatabase(name).drop() }
    }

    void 'every connection sets the auditor properties from the application context of the publisher'() {
        given:
        context = new GenericApplicationContext()
        context.registerBean(AuditorAware, { { -> Optional.of('auditor') } as AuditorAware<String> } as Supplier<AuditorAware>)
        context.refresh()
        datastore = new MongoDatastore(configuration(), new ConfigurableApplicationContextEventPublisher(context), AuditedMongoNote)
        AuditedMongoNote onDefault = new AuditedMongoNote(text: 'default')
        AuditedMongoNote onOther = new AuditedMongoNote(text: 'other')

        when:
        onDefault.save(flush: true)
        AuditedMongoNote.other.withSession {
            AuditedMongoNote.other.save(onOther, [flush: true])
        }

        then:
        AuditedMongoNote.other.withSession { AuditedMongoNote.other.count() } == 1
        onDefault.createdBy == 'auditor'
        onOther.createdBy == 'auditor'
        onOther.lastModifiedBy == 'auditor'
        !AuditedMongoNote.other.autoTimestampEventListener.is(AuditedMongoNote.autoTimestampEventListener)
    }

    void 'every connection keeps a given timestamp when the configuration turns overwriting off'() {
        given:
        datastore = new MongoDatastore(configuration() + [(Settings.SETTING_AUTO_TIMESTAMP_INSERT_OVERWRITE): false], AuditedMongoNote)
        AuditedMongoNote onOther = new AuditedMongoNote(text: 'other', dateCreated: PAST)

        when:
        AuditedMongoNote.other.withSession {
            AuditedMongoNote.other.save(onOther, [flush: true])
        }

        then:
        AuditedMongoNote.other.withNewSession { AuditedMongoNote.other.get(onOther.id).dateCreated } == PAST
        onOther.lastUpdated != null
    }

    private Map<String, Object> configuration() {
        [
                'grails.mongodb.url'        : "mongodb://${mongoHost}:${mongoPort}/timestampConnectionsDefault".toString(),
                'grails.mongodb.connections': [
                        other: [url: "mongodb://${mongoHost}:${mongoPort}/timestampConnectionsOther".toString()]
                ]
        ] as Map<String, Object>
    }
}

@Entity
class AuditedMongoNote {

    String text
    Date dateCreated
    Date lastUpdated

    @CreatedBy
    String createdBy

    @LastModifiedBy
    String lastModifiedBy

    static mapping = {
        connections 'default', 'other'
    }
}
