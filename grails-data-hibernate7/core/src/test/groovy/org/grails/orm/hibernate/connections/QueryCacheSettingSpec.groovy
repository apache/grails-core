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
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.orm.hibernate.HibernateDatastore
import org.hibernate.dialect.H2Dialect
import spock.lang.Issue
import spock.lang.Specification

/**
 * The documented setting hibernate.cache.queries enables the query cache of Hibernate.
 */
@Issue('https://github.com/apache/grails-core/issues/16874')
class QueryCacheSettingSpec extends Specification {

    private static boolean queryCacheEnabled(Map settings) {
        Map config = [
                'dataSource.url'      : "jdbc:h2:mem:queryCache${UUID.randomUUID()};LOCK_TIMEOUT=10000".toString(),
                'dataSource.dbCreate' : 'update',
                'dataSource.dialect'  : H2Dialect.name,
                'hibernate.hbm2ddl.auto': 'create',
                'hibernate.cache'     : ['use_second_level_cache': true, 'region.factory_class': 'org.hibernate.cache.jcache.internal.JCacheRegionFactory'],
        ] + settings
        HibernateDatastore datastore = new HibernateDatastore(DatastoreUtils.createPropertyResolver(config), QueryCacheEntity)
        try {
            return datastore.sessionFactory.sessionFactoryOptions.queryCacheEnabled
        } finally {
            datastore.close()
        }
    }

    void 'hibernate.cache.queries #queries and hibernate.cache.use_query_cache #explicit give a query cache that is #expected'() {
        expect:
        queryCacheEnabled(settings) == expected

        where:
        queries | explicit | expected | settings
        true    | null     | true     | ['hibernate.cache.queries': 'true']
        false   | null     | false    | ['hibernate.cache.queries': 'false']
        null    | null     | false    | [:]
        false   | true     | true     | ['hibernate.cache.queries': 'false', 'hibernate.cache.use_query_cache': 'true']
        true    | false    | false    | ['hibernate.cache.queries': 'true', 'hibernate.cache.use_query_cache': 'false']
    }

    void 'the setting in the nested form enables the query cache'() {
        expect:
        queryCacheEnabled(['hibernate': ['cache': ['queries': true]]])
    }
}

@Entity
class QueryCacheEntity {
    String name
}
