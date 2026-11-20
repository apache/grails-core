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
import org.grails.orm.hibernate.HibernateDatastore
import org.grails.orm.hibernate.cfg.Settings
import org.hibernate.resource.jdbc.spi.StatementInspector
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

/**
 * The total of a page of a criteria grouped by one property is counted by the database, as the distinct values of the
 * property, and not by loading the groups to count them.
 */
@Issue('https://github.com/apache/grails-core/issues/14431')
class GroupedTotalCountSqlSpec extends Specification {

    @Shared List<String> statements = Collections.synchronizedList(new ArrayList<String>())

    @Shared @AutoCleanup HibernateDatastore hibernateDatastore = new HibernateDatastore(
            DatastoreUtils.createPropertyResolver(
                    (Settings.SETTING_DB_CREATE): 'create-drop',
                    'hibernate.session_factory.statement_inspector': new GroupedSqlCapture(statements)
            ),
            GroupedSqlItem
    )

    @Rollback
    void "the total of a page grouped by one property is counted by the database"() {
        given:
        ['a', 'a', 'b', 'c', null].eachWithIndex { String city, int i ->
            new GroupedSqlItem(name: "item$i", city: city).save(flush: true, failOnError: true)
        }
        GroupedSqlItem.withSession { it.clear() }

        when:
        def page = GroupedSqlItem.createCriteria().list(max: 2) {
            projections {
                groupProperty('city')
            }
            order('city')
        }
        statements.clear()
        int total = page.totalCount

        then: 'a, b, c and the group of the nulls, from queries that return one number each'
        total == 4
        statements.size() == 2
        statements.every { it.toLowerCase().startsWith('select count(') }
        statements.any { it.toLowerCase().contains('count(distinct') }
    }

    static class GroupedSqlCapture implements StatementInspector {
        private final List<String> statements

        GroupedSqlCapture(List<String> statements) {
            this.statements = statements
        }

        @Override
        String inspect(String sql) {
            statements.add(sql)
            return sql
        }
    }
}

@Entity
class GroupedSqlItem {
    String name
    String city

    static constraints = {
        city nullable: true
    }
}
