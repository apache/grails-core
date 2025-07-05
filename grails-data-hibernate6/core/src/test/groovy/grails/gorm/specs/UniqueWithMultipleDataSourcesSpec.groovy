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
package grails.gorm.specs

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
        manager.addAllDomainClasses([Abc])
        manager.grailsConfig = [
                'dataSource.url'        : "jdbc:h2:mem:grailsDB;LOCK_TIMEOUT=10000",
                'dataSource.dbCreate'   : 'update',
                'dataSource.dialect'    : H2Dialect.name,
                'dataSource.formatSql'  : 'true',
                'hibernate.flush.mode'  : 'COMMIT',
                'hibernate.cache.queries': 'true',
                'hibernate.cache'       : ['use_second_level_cache': true, 'region.factory_class': 'org.hibernate.cache.jcache.internal.JCacheRegionFactory'],
                'hibernate.hbm2ddl.auto': 'create',
                'dataSources.second'    : [url: "jdbc:h2:mem:second;LOCK_TIMEOUT=10000"],
        ]
    }

    @Rollback
    @Issue('https://github.com/grails/grails-core/issues/10481')
    void "test multiple data sources and unique constraint"() {
        when:
        Abc abc = new Abc(temp: "testing")
        abc.save()

        Abc abc1 = new Abc(temp: "testing")
        Abc.second.withNewSession {
            abc1.second.save()
        }

        then:
        !abc1.hasErrors()
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

