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

import java.util.Locale

import grails.gorm.annotation.Entity
import grails.gorm.transactions.Rollback
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.orm.hibernate.cfg.Settings
import org.hibernate.resource.jdbc.spi.StatementInspector
import org.springframework.transaction.PlatformTransactionManager
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class HibernateGormStaticApiFindWhereSpec extends Specification {

    @Shared SqlCapture sqlCapture = new SqlCapture()

    @Shared @AutoCleanup HibernateDatastore hibernateDatastore = new HibernateDatastore(
            DatastoreUtils.createPropertyResolver(
                    (Settings.SETTING_DB_CREATE): 'create-drop',
                    'hibernate.session_factory.statement_inspector': sqlCapture
            ),
            FindWhereLimitEntity
    )
    @Shared PlatformTransactionManager transactionManager = hibernateDatastore.getTransactionManager()

    @Rollback
    void 'findWhere limits duplicate matches to one row'() {
        given:
        new FindWhereLimitEntity(name: 'duplicate').save(flush: true, failOnError: true)
        new FindWhereLimitEntity(name: 'duplicate').save(flush: true, failOnError: true)
        sqlCapture.clear()

        when:
        FindWhereLimitEntity result = FindWhereLimitEntity.findWhere(name: 'duplicate')

        then:
        result.name == 'duplicate'
        sqlCapture.statements.any { String sql ->
            String normalized = sql.toLowerCase(Locale.ENGLISH)
            normalized.contains('where') && normalized.contains('fetch first') && normalized.contains('rows only')
        }
    }

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
class FindWhereLimitEntity {
    String name
}
