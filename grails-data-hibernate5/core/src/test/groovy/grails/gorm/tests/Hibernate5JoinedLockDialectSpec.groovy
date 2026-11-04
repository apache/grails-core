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

import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLTimeoutException
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

import org.hibernate.LockMode
import org.hibernate.Session
import org.hibernate.dialect.H2Dialect
import org.hibernate.persister.entity.AbstractEntityPersister

import grails.gorm.annotation.Entity

/**
 * A refresh under a pessimistic lock loads through Hibernate's entity loader, and Hibernate silently
 * substitutes a plain read loader when the entity spans several tables, has subclasses, and the dialect
 * cannot lock an outer-joined row. PostgreSQL, DB2 and CockroachDB report exactly that, so this spec runs
 * a dialect that does the same to prove the row is still locked.
 */
class Hibernate5JoinedLockDialectSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(Hibernate5JoinedLockRoot, Hibernate5JoinedLockSub)
        manager.grailsConfig['dataSource.dialect'] = NoOuterJoinForUpdateDialect.name
    }

    void 'refresh(lock: true) locks the row of a #description even when the dialect cannot lock an outer join'() {
        given:
        Long id = create().save(flush: true, failOnError: true).id
        manager.transactionManager.commit(manager.transactionStatus)
        manager.transactionStatus = null

        expect: 'the dialect is one that makes Hibernate fall back to an unlocked loader'
        !manager.sessionFactory.jdbcServices.dialect.supportsOuterJoinForUpdate()

        when: 'the instance is reloaded under a write lock and the transaction is held open'
        boolean granted = Hibernate5JoinedLockRoot.withNewSession {
            Hibernate5JoinedLockRoot.withTransaction {
                def instance = entityClass.get(id)
                instance.refresh(lock: true)
                rootRowLockGranted(id)
            }
        }

        then: 'a competing SELECT ... FOR UPDATE on the root row, which holds the version, is refused'
        !granted

        where:
        description             | entityClass                 | create
        'hierarchy root'        | Hibernate5JoinedLockRoot    | { new Hibernate5JoinedLockRoot(title: 'root') }
        'joined-table subclass' | Hibernate5JoinedLockSub     | { new Hibernate5JoinedLockSub(title: 'sub', extra: 'x') }
    }

    void 'refresh(lock: true) on a #description waits for a competing writer rather than reading the row first'() {
        given:
        Long id = create().save(flush: true, failOnError: true).id
        manager.transactionManager.commit(manager.transactionStatus)
        manager.transactionStatus = null
        def executor = Executors.newSingleThreadExecutor()
        def loaded = new CountDownLatch(1)
        def refreshed = new CountDownLatch(1)
        Connection competitor = openCompetingConnection(10000)

        when: 'a competitor updates the root row, which holds the version, and keeps its transaction open'
        competitor.prepareStatement("update ${rootTableName()} set title = 'competing commit', version = version + 1 where id = ?".toString())
                .withCloseable { statement ->
                    statement.setLong(1, id)
                    assert statement.executeUpdate() == 1
                }
        def refreshing = executor.submit({
            Hibernate5JoinedLockRoot.withNewSession { Session session ->
                Hibernate5JoinedLockRoot.withTransaction {
                    def instance = entityClass.get(id)
                    assert instance.version == 0
                    loaded.countDown()
                    assert instance.refresh(lock: true).is(instance)
                    assert instance.title == 'competing commit'
                    assert instance.version == 1
                    assert session.getCurrentLockMode(instance) == LockMode.PESSIMISTIC_WRITE
                    refreshed.countDown()
                }
            }
        } as Callable)

        then: 'the lock is taken before the state is read, so the refresh waits instead of reading the row it is about to fail a version check on'
        loaded.await(10, TimeUnit.SECONDS) || refreshing.get(1, TimeUnit.SECONDS)
        !refreshed.await(200, TimeUnit.MILLISECONDS)
        !refreshing.isDone()

        when:
        competitor.commit()
        refreshing.get(10, TimeUnit.SECONDS)

        then: 'and then reloads the committed state and version under the lock it holds'
        refreshed.count == 0

        cleanup:
        competitor?.rollback()
        competitor?.close()
        executor?.shutdownNow()
        assert executor == null || executor.awaitTermination(15, TimeUnit.SECONDS)

        where:
        description             | entityClass              | create
        'hierarchy root'        | Hibernate5JoinedLockRoot | { new Hibernate5JoinedLockRoot(title: 'original') }
        'joined-table subclass' | Hibernate5JoinedLockSub  | { new Hibernate5JoinedLockSub(title: 'original', extra: 'x') }
    }

    private Connection openCompetingConnection(int lockTimeoutMillis) {
        Map<String, String> jdbc = Hibernate5JoinedLockRoot.withNewSession { Session session ->
            session.doReturningWork { Connection connection ->
                [url: connection.metaData.URL.tokenize(';')[0], user: connection.metaData.userName]
            }
        }
        Connection connection = DriverManager.getConnection("${jdbc.url};LOCK_TIMEOUT=${lockTimeoutMillis}".toString(), jdbc.user, '')
        connection.autoCommit = false
        connection
    }

    private String rootTableName() {
        ((AbstractEntityPersister) manager.sessionFactory.metamodel
                .entityPersister(Hibernate5JoinedLockRoot.name)).tableName
    }

    private boolean rootRowLockGranted(Long id) {
        String table = rootTableName()
        Map<String, String> jdbc = Hibernate5JoinedLockRoot.withNewSession { Session session ->
            session.doReturningWork { Connection connection ->
                [url: connection.metaData.URL.tokenize(';')[0], user: connection.metaData.userName]
            }
        }
        Connection connection = DriverManager.getConnection("${jdbc.url};LOCK_TIMEOUT=300".toString(), jdbc.user, '')
        connection.autoCommit = false
        try {
            connection.prepareStatement("select id from ${table} where id = ? for update".toString()).withCloseable { statement ->
                statement.setLong(1, id)
                statement.executeQuery().withCloseable { it.next() }
            }
        } catch (SQLTimeoutException ignored) {
            false
        } finally {
            connection.rollback()
            connection.close()
        }
    }
}

@Entity
class Hibernate5JoinedLockRoot {
    Long id
    Long version
    String title

    static mapping = {
        tablePerHierarchy false
    }
}

@Entity
class Hibernate5JoinedLockSub extends Hibernate5JoinedLockRoot {
    String extra
}

class NoOuterJoinForUpdateDialect extends H2Dialect {

    @Override
    boolean supportsOuterJoinForUpdate() {
        false
    }
}
