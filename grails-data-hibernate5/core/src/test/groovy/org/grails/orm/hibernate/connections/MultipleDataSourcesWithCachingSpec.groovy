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
import spock.lang.Specification

/**
 * Created by graemerocher on 15/07/2016.
 */
class MultipleDataSourcesWithCachingSpec extends Specification {

    void "Test map to multiple data sources"() {
        given:"A configuration for multiple data sources"
        Map config = [
                'dataSource.url':"jdbc:h2:mem:grailsDB;LOCK_TIMEOUT=10000",
                'dataSource.dbCreate': 'update',
                'dataSource.dialect': H2Dialect.name,
                'dataSource.formatSql': 'true',
                'hibernate.flush.mode': 'COMMIT',
                'hibernate.cache.queries': 'true',
                'hibernate.cache':['use_second_level_cache':true,'region.factory_class':'org.hibernate.cache.ehcache.EhCacheRegionFactory'],
                'hibernate.hbm2ddl.auto': 'create',
                'dataSources.books':[url:"jdbc:h2:mem:books;LOCK_TIMEOUT=10000"],
                'dataSources.moreBooks':[url:"jdbc:h2:mem:moreBooks;LOCK_TIMEOUT=10000"]
        ]

        when:
        HibernateDatastore datastore = new HibernateDatastore(DatastoreUtils.createPropertyResolver(config),CachingBook )
        CachingBook book = CachingBook.withTransaction {
            new CachingBook(name:"The Stand").save(flush:true)
            CachingBook.get( CachingBook.first().id )

        }

        then:
        book != null

    }
}
@Entity
class CachingBook {
    Long id
    Long version
    String name

    static mapping = {
        cache true
        datasources( ['books', 'moreBooks'] )
    }
    static constraints = {
        name blank:false
    }
}


