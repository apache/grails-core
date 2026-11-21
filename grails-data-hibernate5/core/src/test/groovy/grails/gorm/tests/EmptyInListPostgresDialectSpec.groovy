/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package grails.gorm.tests

import grails.gorm.annotation.Entity
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.orm.hibernate.HibernateDatastore
import org.grails.orm.hibernate.cfg.Settings
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

/**
 * H2's own dialect accepts an empty IN list, so a spec on it never shows the failure. With the PostgreSQL dialect on H2
 * in its PostgreSQL mode, Hibernate renders an empty list as {@code in (null)}, which is never true, and its negation
 * returns nothing: the entry points that passed their values to Hibernate unchanged show that here.
 */
@Issue('https://github.com/apache/grails-core/issues/17008')
class EmptyInListPostgresDialectSpec extends Specification {

    @Shared
    @AutoCleanup
    HibernateDatastore datastore = new HibernateDatastore(DatastoreUtils.createPropertyResolver([
        (Settings.SETTING_DB_CREATE): 'create-drop',
        'dataSource.url'            : 'jdbc:h2:mem:emptyInPostgres;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;LOCK_TIMEOUT=10000',
        'hibernate.dialect'         : 'org.hibernate.dialect.PostgreSQL10Dialect'
    ]), PgListBook)

    void setup() {
        PgListBook.withNewTransaction {
            ['alpha', 'beta', 'gamma'].each { new PgListBook(title: it).save(failOnError: true) }
        }
    }

    void cleanup() {
        PgListBook.withNewTransaction { PgListBook.executeUpdate('delete from PgListBook') }
    }

    @Unroll
    void "an empty list on the PostgreSQL dialect: #form"() {
        expect:
        PgListBook.withNewSession { query.call() } == expected

        where:
        form                                    | query                                                                                                          | expected
        'criteria, a list'                      | { -> PgListBook.createCriteria().list { not { inList('title', []) }; order('title') }*.title }               | ['alpha', 'beta', 'gamma']
        'criteria, an array'                    | { -> PgListBook.createCriteria().list { not { inList('title', [] as Object[]) }; order('title') }*.title }   | ['alpha', 'beta', 'gamma']
        'criteria, an array, not negated'       | { -> PgListBook.createCriteria().list { inList('title', [] as Object[]) }*.title }                           | []
        'where query, negated'                  | { -> PgListBook.where { !(title in []) }.list(sort: 'title')*.title }                                       | ['alpha', 'beta', 'gamma']
        'where query'                           | { -> PgListBook.where { title in [] }.list()*.title }                                                       | []
        'detached criteria, negated'            | { -> new grails.gorm.DetachedCriteria(PgListBook).build { not { inList('title', []) } }.list(sort: 'title')*.title } | ['alpha', 'beta', 'gamma']
        'dynamic finder, an empty list'         | { -> PgListBook.findAllByTitleInList([])*.title }                                                           | []
    }
}

@Entity
class PgListBook {
    String title
}
