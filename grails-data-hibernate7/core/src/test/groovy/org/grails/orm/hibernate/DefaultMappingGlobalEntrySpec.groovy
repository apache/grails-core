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
package org.grails.orm.hibernate

import java.sql.Connection
import java.sql.ResultSet

import grails.gorm.annotation.Entity
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.orm.hibernate.cfg.Settings

/**
 * A {@code '*'} entry in {@code grails.gorm.default.mapping} applies to every property of every domain class, and
 * a domain class's own mapping still configures its properties on top of it.
 */
class DefaultMappingGlobalEntrySpec extends Specification {

    @Shared
    @AutoCleanup
    HibernateDatastore withGlobalEntry = new HibernateDatastore(DatastoreUtils.createPropertyResolver(
            (Settings.SETTING_DB_CREATE): 'create-drop',
            'dataSource.url'             : 'jdbc:h2:mem:defaultMappingGlobalEntry;LOCK_TIMEOUT=10000',
            'grails.gorm.default.mapping': {
                '*'(cascadeValidate: 'dirty')
            }
    ), GlobalEntryVersionedBook)

    @Shared
    @AutoCleanup
    HibernateDatastore withoutGlobalEntry = new HibernateDatastore(DatastoreUtils.createPropertyResolver(
            (Settings.SETTING_DB_CREATE): 'create-drop',
            'dataSource.url'             : 'jdbc:h2:mem:defaultMappingNoGlobalEntry;LOCK_TIMEOUT=10000',
            'grails.gorm.default.mapping': {
                cache false
            }
    ), PlainVersionedBook)

    void "a version column the mapping names is created"() {
        expect:
        columnsOf(withoutGlobalEntry, 'PLAIN_VERSIONED_BOOK').contains('ROW_VERSION')
    }

    void "a version column the mapping names is created when the default mapping has a '*' entry"() {
        expect:
        columnsOf(withGlobalEntry, 'GLOBAL_ENTRY_VERSIONED_BOOK').contains('ROW_VERSION')
    }

    private static Set<String> columnsOf(HibernateDatastore datastore, String table) {
        Set<String> columns = [] as Set
        datastore.sessionFactory.openSession().withCloseable { session ->
            session.doWork { Connection connection ->
                ResultSet rs = connection.metaData.getColumns(null, null, table, null)
                while (rs.next()) {
                    columns << rs.getString('COLUMN_NAME')
                }
            }
        }
        columns
    }
}

@Entity
class GlobalEntryVersionedBook {
    String title

    static mapping = {
        version 'row_version'
    }
}

@Entity
class PlainVersionedBook {
    String title

    static mapping = {
        version 'row_version'
    }
}
