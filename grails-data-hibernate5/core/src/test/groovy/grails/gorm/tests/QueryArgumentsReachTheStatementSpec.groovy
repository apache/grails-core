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

import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.PreparedStatement

import javax.sql.DataSource

import org.h2.jdbcx.JdbcDataSource
import org.springframework.jdbc.datasource.DelegatingDataSource
import org.springframework.transaction.PlatformTransactionManager

import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import grails.gorm.annotation.Entity
import grails.gorm.transactions.Rollback
import grails.orm.HibernateCriteriaBuilder
import org.grails.datastore.gorm.events.DefaultApplicationEventPublisher
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.orm.hibernate.HibernateDatastore
import org.grails.orm.hibernate.cfg.Settings

/**
 * The fetchSize and timeout arguments of a query are set on the JDBC statement that runs it, and not only checked: the
 * data source of this datastore records what Hibernate sets on each statement.
 */
@Issue('https://github.com/apache/grails-core/issues/16808')
class QueryArgumentsReachTheStatementSpec extends Specification {

    @Shared RecordingDataSource dataSource = new RecordingDataSource(h2())

    @Shared @AutoCleanup HibernateDatastore hibernateDatastore = new HibernateDatastore(
            dataSource,
            DatastoreUtils.createPropertyResolver((Settings.SETTING_DB_CREATE): 'create-drop'),
            new DefaultApplicationEventPublisher(),
            QargsItem
    )

    @Shared PlatformTransactionManager transactionManager = hibernateDatastore.getTransactionManager()

    private static DataSource h2() {
        JdbcDataSource h2 = new JdbcDataSource()
        h2.setURL('jdbc:h2:mem:queryArgumentsReachTheStatement;DB_CLOSE_DELAY=-1')
        h2
    }

    private void saveItems() {
        new QargsItem(name: 'b', age: 20).save()
        new QargsItem(name: 'a', age: 30).save(flush: true)
        QargsItem.withSession { it.clear() }
        dataSource.settings.clear()
    }

    @Rollback
    @Unroll
    void 'the fetchSize and timeout of #route are set on the statement'() {
        given:
        saveItems()

        when:
        List found = query.call()

        then:
        found*.name.sort() == expected
        dataSource.settings.contains('setFetchSize:50')
        dataSource.settings.contains('setQueryTimeout:30')

        where:
        route             | expected   | query
        'where.list'      | ['a', 'b'] | { -> QargsItem.where { age > 0 }.list(fetchSize: 50, timeout: 30) }
        'findAll closure' | ['a', 'b'] | { -> QargsItem.findAll(fetchSize: 50, timeout: 30) { age > 0 } }
        'findAllBy'       | ['a', 'b'] | { -> QargsItem.findAllByAgeGreaterThan(0, [fetchSize: 50, timeout: 30]) }
        'findBy'          | ['a']      | { -> [QargsItem.findByName('a', [fetchSize: 50, timeout: 30])] }
        'paged where'     | ['a', 'b'] | { -> QargsItem.where { age > 0 }.list(max: 5, fetchSize: 50, timeout: 30) }
        'paged criteria'  | ['a', 'b'] | { -> QargsItem.createCriteria().list(max: 5, fetchSize: 50, timeout: 30) { gt('age', 0) } }
    }

    @Rollback
    @Unroll
    void 'the count behind the total of a #route is run with the timeout of the page'() {
        given:
        saveItems()
        def page = query.call()
        dataSource.settings.clear()

        expect:
        page.totalCount == 2
        dataSource.settings.contains('setQueryTimeout:30')

        where:
        route            | query
        'paged where'    | { -> QargsItem.where { age > 0 }.list(max: 1, timeout: 30) }
        'paged criteria' | { -> QargsItem.createCriteria().list(max: 1, timeout: 30) { gt('age', 0) } }
    }

    @Rollback
    @Unroll
    void 'a paged criteria reads the arguments #arguments once, as the other queries do'() {
        given:
        saveItems()

        when:
        List found = QargsItem.createCriteria().list([max: 5] + arguments) { gt('age', 0) }

        then:
        found*.name.sort() == ['a', 'b']
        dataSource.settings.containsAll(recorded)

        where:
        arguments                                | recorded
        [timeout: '9.0']                         | ['setQueryTimeout:9']
        [timeout: null]                          | []
        [flushMode: ' MANUAL ']                  | []
        [fetchSize: '50', timeout: '30']         | ['setFetchSize:50', 'setQueryTimeout:30']
    }

    @Rollback
    void 'a criteria builder made from a session factory alone lists a page with its arguments'() {
        given:
        saveItems()

        when:
        List found = new HibernateCriteriaBuilder(QargsItem, hibernateDatastore.sessionFactory).list(sort: 'name', fetchSize: 50) { }

        then:
        found*.name == ['a', 'b']
        dataSource.settings.contains('setFetchSize:50')
    }

    /**
     * Records the fetch size and the query timeout that are set on each prepared statement of its connections.
     */
    static class RecordingDataSource extends DelegatingDataSource {

        final List<String> settings = Collections.synchronizedList(new ArrayList<String>())

        RecordingDataSource(DataSource target) {
            super(target)
        }

        @Override
        Connection getConnection() {
            wrap(super.getConnection())
        }

        @Override
        Connection getConnection(String username, String password) {
            wrap(super.getConnection(username, password))
        }

        private Connection wrap(Connection connection) {
            (Connection) Proxy.newProxyInstance(Connection.classLoader, [Connection] as Class[], { Object proxy, Method method, Object[] args ->
                Object result = invokeOn(connection, method, args)
                result instanceof PreparedStatement ? record((PreparedStatement) result) : result
            } as InvocationHandler)
        }

        private PreparedStatement record(PreparedStatement statement) {
            (PreparedStatement) Proxy.newProxyInstance(PreparedStatement.classLoader, [PreparedStatement] as Class[], { Object proxy, Method method, Object[] args ->
                if (method.name in ['setFetchSize', 'setQueryTimeout']) {
                    settings.add("${method.name}:${args[0]}".toString())
                }
                invokeOn(statement, method, args)
            } as InvocationHandler)
        }

        private static Object invokeOn(Object target, Method method, Object[] args) {
            try {
                method.invoke(target, args)
            }
            catch (InvocationTargetException e) {
                throw e.cause
            }
        }
    }
}

@Entity
class QargsItem {
    String name
    Integer age
}
