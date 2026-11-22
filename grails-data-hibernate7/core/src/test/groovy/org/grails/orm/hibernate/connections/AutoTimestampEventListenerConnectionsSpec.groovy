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

import java.util.function.Supplier

import org.hibernate.dialect.H2Dialect
import spock.lang.AutoCleanup
import spock.lang.Specification

import org.springframework.context.support.GenericApplicationContext

import grails.gorm.annotation.CreatedBy
import grails.gorm.annotation.Entity
import grails.gorm.annotation.LastModifiedBy
import org.grails.datastore.gorm.timestamp.AuditorAware
import org.grails.datastore.mapping.config.Settings
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.orm.hibernate.HibernateDatastore

/**
 * The listener of every connection, not only that of the default one, sets the auditor properties and follows the
 * auto-timestamp settings of the configuration.
 */
class AutoTimestampEventListenerConnectionsSpec extends Specification {

    private static final Date PAST = new Date(110_000_000_000L)

    @AutoCleanup
    GenericApplicationContext context

    @AutoCleanup
    HibernateDatastore datastore

    void 'every connection sets the auditor properties from the application context of the datastore'() {
        given:
        context = new GenericApplicationContext()
        context.registerBean(AuditorAware, { { -> Optional.of('auditor') } as AuditorAware<String> } as Supplier<AuditorAware>)
        context.refresh()
        datastore = new HibernateDatastore(DatastoreUtils.createPropertyResolver(configuration()), AuditedConnectionNote)
        datastore.setApplicationContext(context)
        AuditedConnectionNote onDefault = new AuditedConnectionNote(text: 'default')
        AuditedConnectionNote onSecondary = new AuditedConnectionNote(text: 'secondary')

        when:
        AuditedConnectionNote.withTransaction {
            onDefault.save(flush: true)
        }
        AuditedConnectionNote.secondary.withTransaction {
            onSecondary.secondary.save(flush: true)
        }

        then:
        onDefault.createdBy == 'auditor'
        onDefault.lastModifiedBy == 'auditor'
        onSecondary.createdBy == 'auditor'
        onSecondary.lastModifiedBy == 'auditor'
    }

    void 'every connection keeps a given timestamp when the configuration turns overwriting off'() {
        given:
        datastore = new HibernateDatastore(DatastoreUtils.createPropertyResolver(configuration() + [
                (Settings.SETTING_AUTO_TIMESTAMP_INSERT_OVERWRITE): false
        ]), AuditedConnectionNote)
        AuditedConnectionNote onDefault = new AuditedConnectionNote(text: 'default', dateCreated: PAST)
        AuditedConnectionNote onSecondary = new AuditedConnectionNote(text: 'secondary', dateCreated: PAST)

        when:
        AuditedConnectionNote.withTransaction {
            onDefault.save(flush: true)
        }
        AuditedConnectionNote.secondary.withTransaction {
            onSecondary.secondary.save(flush: true)
        }

        then:
        AuditedConnectionNote.withNewTransaction { AuditedConnectionNote.get(onDefault.id).dateCreated } == PAST
        AuditedConnectionNote.secondary.withNewTransaction { AuditedConnectionNote.secondary.get(onSecondary.id).dateCreated } == PAST
        onSecondary.lastUpdated != null
    }

    private static Map<String, Object> configuration() {
        [
                'dataSource.url'        : 'jdbc:h2:mem:timestampConnectionsDefaultDB;LOCK_TIMEOUT=10000',
                'dataSource.dbCreate'   : 'create-drop',
                'dataSource.dialect'    : H2Dialect.name,
                'hibernate.hbm2ddl.auto': 'create',
                'dataSources.secondary' : [url: 'jdbc:h2:mem:timestampConnectionsSecondaryDB;LOCK_TIMEOUT=10000']
        ] as Map<String, Object>
    }
}

@Entity
class AuditedConnectionNote {

    String text
    Date dateCreated
    Date lastUpdated

    @CreatedBy
    String createdBy

    @LastModifiedBy
    String lastModifiedBy

    static constraints = {
        createdBy nullable: true
        lastModifiedBy nullable: true
    }

    static mapping = {
        datasource 'ALL'
    }
}
