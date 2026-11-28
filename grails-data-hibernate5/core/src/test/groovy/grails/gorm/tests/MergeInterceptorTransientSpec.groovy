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
import org.hibernate.EmptyInterceptor
import org.hibernate.resource.jdbc.spi.StatementInspector
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

/**
 * A Hibernate interceptor may say that no instance is transient. Merge of a new instance with an assigned id then goes
 * the way of a detached one, finds no row and inserts it, and the insert is the only write.
 */
@Issue('https://github.com/apache/grails-core/issues/14354')
class MergeInterceptorTransientSpec extends Specification {

    @Shared List<String> statements = Collections.synchronizedList(new ArrayList<String>())

    @Shared @AutoCleanup HibernateDatastore hibernateDatastore = new HibernateDatastore(
            DatastoreUtils.createPropertyResolver(
                    (Settings.SETTING_DB_CREATE): 'create-drop',
                    'hibernate.session_factory.interceptor': new NeverTransientInterceptor(),
                    'hibernate.session_factory.statement_inspector': new MergeSqlCapture(statements)
            ),
            MergeInterceptedThing
    )

    @Rollback
    void "merge of a new instance with an assigned id that the interceptor calls not transient is inserted once"() {
        given:
        statements.clear()

        when:
        MergeInterceptedThing merged = new MergeInterceptedThing(id: 'one', title: 'x').merge(flush: true)

        then: 'the row is inserted and not updated by the same flush'
        merged.id == 'one'
        MergeInterceptedThing.count() == 1
        statements.count { it.toLowerCase().startsWith('insert') } == 1
        statements.count { it.toLowerCase().startsWith('update') } == 0
    }

    static class NeverTransientInterceptor extends EmptyInterceptor {
        @Override
        Boolean isTransient(Object entity) {
            Boolean.FALSE
        }
    }

    static class MergeSqlCapture implements StatementInspector {
        private final List<String> statements

        MergeSqlCapture(List<String> statements) {
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
class MergeInterceptedThing {
    String id
    String title

    static mapping = {
        id generator: 'assigned'
    }
}
