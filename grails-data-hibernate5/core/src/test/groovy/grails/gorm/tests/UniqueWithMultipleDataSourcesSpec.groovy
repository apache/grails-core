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

import grails.gorm.annotation.Entity
import grails.gorm.transactions.Rollback
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.orm.hibernate.HibernateDatastore
import org.hibernate.dialect.H2Dialect
import org.springframework.transaction.PlatformTransactionManager
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

/**
 * Created by graemerocher on 17/02/2017.
 */
class UniqueWithMultipleDataSourcesSpec extends Specification {

    @Shared
    Map config = [
            'dataSource.url'        : "jdbc:h2:mem:grailsDB;LOCK_TIMEOUT=10000",
            'dataSource.dbCreate'   : 'update',
            'dataSource.dialect'    : H2Dialect.name,
            'dataSource.formatSql'  : 'true',
            'hibernate.flush.mode'  : 'COMMIT',
            'hibernate.cache.queries': 'true',
            'hibernate.cache'       : ['use_second_level_cache': true, 'region.factory_class': 'org.hibernate.cache.ehcache.EhCacheRegionFactory'],
            'hibernate.hbm2ddl.auto': 'create',
            'dataSources.second'    : [url: "jdbc:h2:mem:second;LOCK_TIMEOUT=10000"],
    ]

    @Shared
    @AutoCleanup
    HibernateDatastore hibernateDatastore = new HibernateDatastore(DatastoreUtils.createPropertyResolver(config), Abc)
    @Shared
    PlatformTransactionManager transactionManager = hibernateDatastore.transactionManager

    @Rollback
    @Issue('https://github.com/apache/grails-core/issues/10481')
    void "a value that is unique on its own connection does not conflict with the same value on another"() {
        when: 'the value is saved on the default connection, and the same value on the second'
        Abc abc = new Abc(temp: "testing")
        abc.save(flush: true)

        Abc abc1 = new Abc(temp: "testing")
        Abc.second.withNewTransaction {
            abc1.second.save(flush: true)
        }

        then: 'the unique check of the second save looks at the second connection only'
        !abc1.hasErrors()
    }

    @Issue('https://github.com/apache/grails-core/issues/16934')
    void "a save through the API of the default connection checks the default connection, not the block around it"() {
        given: 'a value that exists on the second connection only'
        Abc.second.withNewTransaction {
            new Abc(temp: 'scoped-only-second').second.save(validate: false, flush: true)
        }

        when: 'the default connection API saves it inside a block of the second'
        Abc abc = new Abc(temp: 'scoped-only-second')
        Abc saved = Abc.withNewSession {
            Abc.second.withNewSession {
                Abc.DEFAULT.save(abc)
            }
        }

        then: 'the default connection has no such row, so the save goes through'
        abc.errors.allErrors.toString() == '[]'
        saved != null

        cleanup:
        Abc.second.withNewTransaction { Abc.second.executeUpdate('delete from Abc where temp = :t', [t: 'scoped-only-second']) }
        Abc.withNewTransaction { Abc.executeUpdate('delete from Abc where temp = :t', [t: 'scoped-only-second']) }
    }
}

@Entity
class Abc {

    String temp

    static constraints = {
        temp unique: true
    }

    static mapping = {
        datasource(ConnectionSource.ALL)
    }
}

