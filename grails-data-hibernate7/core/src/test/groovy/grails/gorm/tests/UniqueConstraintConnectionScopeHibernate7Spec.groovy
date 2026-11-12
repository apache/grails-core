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
package grails.gorm.tests

import org.hibernate.dialect.H2Dialect
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

import grails.gorm.annotation.Entity
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.orm.hibernate.HibernateDatastore

/**
 * The {@code unique} constraint of an entity mapped to every connection checks the connection a connection block
 * names, as the unqualified calls inside the block do, and the default connection outside any block.
 */
@Issue('https://github.com/apache/grails-core/issues/16934')
class UniqueConstraintConnectionScopeHibernate7Spec extends Specification {

    @Shared
    @AutoCleanup
    HibernateDatastore datastore = new HibernateDatastore(DatastoreUtils.createPropertyResolver([
            'dataSource.url'      : 'jdbc:h2:mem:uniqueScopeDefault;LOCK_TIMEOUT=10000',
            'dataSource.dbCreate' : 'create-drop',
            'dataSource.dialect'  : H2Dialect.name,
            'dataSources.second'  : [url: 'jdbc:h2:mem:uniqueScopeSecond;LOCK_TIMEOUT=10000'],
            'dataSources.third'   : [url: 'jdbc:h2:mem:uniqueScopeThird;LOCK_TIMEOUT=10000']
    ]), UnqScopeEntity)

    void cleanup() {
        UnqScopeEntity.withNewTransaction { UnqScopeEntity.executeUpdate('delete from UnqScopeEntity') }
        UnqScopeEntity.second.withNewTransaction { UnqScopeEntity.second.executeUpdate('delete from UnqScopeEntity') }
        UnqScopeEntity.third.withNewTransaction { UnqScopeEntity.third.executeUpdate('delete from UnqScopeEntity') }
    }

    void 'a duplicate that exists only on the connection of the block is rejected inside the block'() {
        given: 'a row on the second connection only'
        UnqScopeEntity.second.withNewTransaction {
            new UnqScopeEntity(name: 'taken').second.save(validate: false, flush: true)
        }

        expect: 'the block checks the second connection'
        UnqScopeEntity.second.withNewSession {
            !new UnqScopeEntity(name: 'taken').validate()
        }

        and: 'the default connection, which has no such row, accepts it'
        UnqScopeEntity.withNewSession {
            new UnqScopeEntity(name: 'taken').validate()
        }
    }

    void 'the check runs on the connection the save names, not on the block around it'() {
        given: 'a row on the second connection only'
        UnqScopeEntity.second.withNewTransaction {
            new UnqScopeEntity(name: 'taken').second.save(validate: false, flush: true)
        }

        when: 'the save names the third connection inside a block of the second, with a session open for the third'
        UnqScopeEntity book = new UnqScopeEntity(name: 'taken')
        UnqScopeEntity saved = UnqScopeEntity.third.withNewSession {
            UnqScopeEntity.second.withNewSession {
                book.third.save()
            }
        }

        then: 'the third has no such row, so the save goes through'
        book.errors.allErrors.toString() == '[]'
        saved != null
    }

    void 'a duplicate that exists only on the default connection is accepted inside the block of the second'() {
        given: 'a row on the default connection only'
        UnqScopeEntity.withNewTransaction {
            new UnqScopeEntity(name: 'taken').save(validate: false, flush: true)
        }

        expect:
        UnqScopeEntity.second.withNewSession {
            new UnqScopeEntity(name: 'taken').validate()
        }

        and: 'the default connection rejects it'
        UnqScopeEntity.withNewSession {
            !new UnqScopeEntity(name: 'taken').validate()
        }
    }
}

@Entity
class UnqScopeEntity {

    String name

    static constraints = {
        name(unique: true)
    }

    static mapping = {
        version false
        datasource(ConnectionSource.ALL)
    }
}
