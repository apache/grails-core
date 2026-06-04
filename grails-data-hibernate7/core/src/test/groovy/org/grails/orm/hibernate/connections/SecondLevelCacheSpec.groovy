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

import grails.gorm.annotation.Entity
import groovy.transform.EqualsAndHashCode
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.orm.hibernate.HibernateDatastore
import org.hibernate.dialect.H2Dialect
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class SecondLevelCacheSpec extends Specification {
    @Shared @AutoCleanup HibernateDatastore datastore
    void setupSpec() {
        Map config = [
                'dataSource.url':"jdbc:h2:mem:grailsDB;LOCK_TIMEOUT=10000",
                'dataSource.dbCreate': 'update',
                'dataSource.dialect': H2Dialect.name,
                'dataSource.formatSql': 'true',
                'dataSource.logSql': 'true',
                'hibernate.flush.mode': 'COMMIT',
                'hibernate.cache.queries': 'true',
                'hibernate.cache': ['use_second_level_cache': true, 'region.factory_class': 'org.hibernate.cache.ehcache.EhCacheRegionFactory'],
                'hibernate.hbm2ddl.auto': 'create',
        ]

        datastore = new HibernateDatastore(DatastoreUtils.createPropertyResolver(config), CachingEntity)
    }

    void "Test second level cache"() {
        when:
        datastore.sessionFactory.getStatistics().setStatisticsEnabled(true)
        def id = CachingEntity.withNewTransaction {
            new CachingEntity(name: 'test').save()
            CachingEntity.first().id
        }

        String[] regionNames = datastore.sessionFactory.getStatistics().getSecondLevelCacheRegionNames()

        then:
        regionNames.size() == 1
        datastore.sessionFactory.getStatistics().getCacheRegionStatistics(regionNames[0]).getMissCount() == 0
        datastore.sessionFactory.getStatistics().getCacheRegionStatistics(regionNames[0]).getHitCount() == 0

        when:
        CachingEntity entity1 = CachingEntity.withNewTransaction {
            CachingEntity.get(id)
        }

        then:
        datastore.sessionFactory.getStatistics().getCacheRegionStatistics(regionNames[0]).getMissCount() == 1
        datastore.sessionFactory.getStatistics().getCacheRegionStatistics(regionNames[0]).getHitCount() == 0
        entity1 != null

        when:
        CachingEntity entity2 = CachingEntity.withNewTransaction {
            CachingEntity.get(id)
        }

        then:
        datastore.sessionFactory.getStatistics().getCacheRegionStatistics(regionNames[0]).getMissCount() == 1
        datastore.sessionFactory.getStatistics().getCacheRegionStatistics(regionNames[0]).getHitCount() == 1
        entity1 == entity2
    }
}

@Entity
@EqualsAndHashCode
class CachingEntity {
    String name

    static mapping = {
        cache true
    }

    static constraints = {
        name blank: false
    }
}
