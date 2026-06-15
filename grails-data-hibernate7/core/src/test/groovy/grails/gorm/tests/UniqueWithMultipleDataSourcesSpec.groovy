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
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.hibernate.dialect.H2Dialect
import spock.lang.*

/**
 * Created by graemerocher on 17/02/2017.
 */
class UniqueWithMultipleDataSourcesSpec extends HibernateGormDatastoreSpec {

    def setupSpec() {
        manager.registerDomainClasses(Abc)
        manager.grailsConfig = [
                'dataSource': [
                        'url'        : "jdbc:h2:mem:grailsDB;LOCK_TIMEOUT=10000",
                        'dbCreate'   : 'update',
                        'dialect'    : H2Dialect.name,
                        'formatSql'  : 'true'
                ],
                'dataSources': [
                        'second': [
                                'url'        : "jdbc:h2:mem:second;LOCK_TIMEOUT=10000",
                                'dbCreate'   : 'update',
                                'dialect'    : H2Dialect.name,
                                'formatSql'  : 'true'
                        ]
                ],
                'hibernate': [
                        'flush.mode'  : 'COMMIT',
                        'cache.queries': 'true',
                        'cache'       : ['use_second_level_cache': true, 'region.factory_class': 'org.hibernate.cache.jcache.internal.JCacheRegionFactory'],
                        'hbm2ddl.auto': 'create-drop'
                ]
        ]
    }
    
    def setup() {
        // The HibernateGormDatastoreSpec only initializes the default datasource by default.
        // We need to explicitly initialize the 'second' datasource to ensure its schema is created.
        manager.getHibernateDatastore().getDatastoreForConnection('second')
    }

    @Rollback
    @Issue('https://github.com/grails/grails-core/issues/10481')
    void "test multiple data sources and unique constraint"() {
        when:
        Abc abc = new Abc(temp: "testing")
        abc.save(flush: true)

        Abc abc1 = new Abc(temp: "testing")
        Abc.second.withNewSession {
            abc1.second.save(flush: true)
        }

        then:
        abc1.hasErrors()
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
