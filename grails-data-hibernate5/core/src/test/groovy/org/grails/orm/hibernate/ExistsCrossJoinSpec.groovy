/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  'License'); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.orm.hibernate

import grails.gorm.annotation.Entity
import grails.gorm.transactions.Rollback
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.orm.hibernate.cfg.Settings
import org.hibernate.resource.jdbc.spi.StatementInspector
import org.springframework.transaction.PlatformTransactionManager
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

@Issue('https://github.com/apache/grails-core/issues/14334')
class ExistsCrossJoinSpec extends Specification {

    @Shared SqlCapture sqlCapture = new SqlCapture()

    @Shared @AutoCleanup HibernateDatastore hibernateDatastore = new HibernateDatastore(
            DatastoreUtils.createPropertyResolver(
                    (Settings.SETTING_DB_CREATE): 'create-drop',
                    'hibernate.session_factory.statement_inspector': sqlCapture
            ),
            ExistsItem
    )
    @Shared PlatformTransactionManager transactionManager = hibernateDatastore.getTransactionManager()

    @Rollback
    void 'exists returns true for existing entity'() {
        given:
        ExistsItem item = new ExistsItem(name: 'alpha').save(flush: true)

        expect:
        ExistsItem.exists(item.id)
    }

    @Rollback
    void 'exists returns false for non-existent id'() {
        expect:
        !ExistsItem.exists(99999)
    }

    @Rollback
    void 'exists does not produce a cross-join'() {
        given:
        new ExistsItem(name: 'one').save(flush: true)
        new ExistsItem(name: 'two').save(flush: true)
        new ExistsItem(name: 'three').save(flush: true)

        when:
        sqlCapture.clear()
        ExistsItem item = new ExistsItem(name: 'target').save(flush: true)
        sqlCapture.clear()
        ExistsItem.exists(item.id)

        then: 'the SQL should contain only a single FROM clause (no cross-join)'
        sqlCapture.statements.any { it.toLowerCase().contains('select count') }

        and: 'there should be exactly one table reference in the FROM clause'
        String countSql = sqlCapture.statements.find { it.toLowerCase().contains('select count') }
        countSql != null
        // A cross-join would have the table name appearing twice after 'from'
        // e.g. 'from exists_item x0_, exists_item x1_' vs correct 'from exists_item x0_'
        countSql.toLowerCase().split('cross join').length == 1
        // Verify no comma-join pattern (two table aliases after FROM)
        !countSql.toLowerCase().matches(/.*from\s+\S+\s+\S+\s*,\s*\S+\s+\S+.*/)
    }

    @Rollback
    void 'exists with multiple rows returns correct result'() {
        given: 'multiple entities in the table'
        ExistsItem target = new ExistsItem(name: 'target').save(flush: true)
        new ExistsItem(name: 'other1').save(flush: true)
        new ExistsItem(name: 'other2').save(flush: true)
        new ExistsItem(name: 'other3').save(flush: true)
        new ExistsItem(name: 'other4').save(flush: true)

        expect: 'exists returns correct results'
        ExistsItem.exists(target.id)
        !ExistsItem.exists(99999)
    }

    /**
     * Captures SQL statements executed by Hibernate for inspection in tests.
     */
    static class SqlCapture implements StatementInspector {

        final List<String> statements = Collections.synchronizedList(new ArrayList<String>())

        @Override
        String inspect(String sql) {
            statements.add(sql)
            return sql
        }

        void clear() {
            statements.clear()
        }
    }
}

@Entity
class ExistsItem {

    String name
}
