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
package grails.gorm.tests.detachedcriteria

import grails.gorm.DetachedCriteria
import grails.gorm.tests.DatabaseContainers
import grails.gorm.tests.HibernateGormDatastoreSpec
import org.grails.datastore.mapping.query.Query
import org.grails.datastore.mapping.query.criteria.FunctionCallingCriterion
import org.testcontainers.mariadb.MariaDBContainer
import org.testcontainers.mysql.MySQLContainer
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.spock.Testcontainers
import spock.lang.IgnoreIf
import spock.lang.Requires
import spock.lang.Shared

/**
 * Runs the functions of a where query, on a read and in a bulk statement, against the databases a string of SQL
 * would differ on: the date functions are not the same function, or not a function at all, on each of them.
 * Oracle and Db2 run only the empty list, the one case they differ on: they reject an empty IN list that the
 * others accept. Db2 is skipped on arm64, where its image does not run.
 */
@Testcontainers
@Requires({ isDockerAvailable() })
class WhereQueryFunctionsDialectSpec extends HibernateGormDatastoreSpec {

    @Shared PostgreSQLContainer postgres = new PostgreSQLContainer('postgres:16')
    @Shared MySQLContainer mysql = new MySQLContainer('mysql:8.0')
    @Shared MariaDBContainer mariadb = new MariaDBContainer('mariadb:10.11')
    @Shared oracle = DatabaseContainers.ORACLE
    @Shared db2 = DatabaseContainers.DB2
    @Shared sqlServer = DatabaseContainers.SQL_SERVER

    void setupSpec() {
        manager.registerDomainClasses(WhereQueryFunctionEntity)
    }

    private void startOn(container) {
        if (!container.isRunning()) {
            container.start()
        }
        manager.destroy()
        manager.grailsConfig = [
            'dataSource.url'             : container.jdbcUrl,
            'dataSource.driverClassName' : container.driverClassName,
            'dataSource.username'        : container.username,
            'dataSource.password'        : container.password,
            'dataSource.dbCreate'        : 'create-drop',
            'hibernate.hbm2ddl.auto'     : 'create',
        ]
        manager.setup(this.class)
    }

    private static DetachedCriteria<WhereQueryFunctionEntity> criteriaOf(String function, Query.PropertyCriterion criterion) {
        DetachedCriteria<WhereQueryFunctionEntity> criteria = new DetachedCriteria<>(WhereQueryFunctionEntity)
        criteria.add(new FunctionCallingCriterion(function, criterion))
        criteria
    }

    private static Date at(int year, int month, int day, int hour, int minute, int second) {
        new GregorianCalendar(year, month - 1, day, hour, minute, second).time
    }

    private void seed() {
        new WhereQueryFunctionEntity(name: 'a', amount: 1, created: at(2011, 3, 4, 5, 6, 7)).save(flush: true)
        new WhereQueryFunctionEntity(name: 'b', amount: 2, created: at(2012, 11, 24, 15, 16, 17)).save(flush: true)
        new WhereQueryFunctionEntity(name: '3.5', amount: 3, created: null).save(flush: true)
    }

    void "the date functions of a where query run on #db, on a read and in a bulk statement"() {
        given:
        startOn(container)
        seed()

        expect:
        criteriaOf('year', new Query.Equals('created', 2011)).list()*.name == ['a']
        criteriaOf('month', new Query.Equals('created', 11)).list()*.name == ['b']
        criteriaOf('day', new Query.Equals('created', 4)).list()*.name == ['a']
        criteriaOf('hour', new Query.Equals('created', 15)).list()*.name == ['b']
        criteriaOf('minute', new Query.Equals('created', 6)).list()*.name == ['a']
        criteriaOf('second', new Query.Equals('created', 17)).list()*.name == ['b']
        criteriaOf('year', new Query.GreaterThan('created', 2011)).list()*.name == ['b']

        when: 'the same functions delete in a bulk statement'
        int byYear = criteriaOf('year', new Query.Equals('created', 2011)).deleteAll()
        int bySecond = criteriaOf('second', new Query.Equals('created', 17)).deleteAll()
        session.clear()

        then:
        byYear == 1
        bySecond == 1
        WhereQueryFunctionEntity.list()*.name == ['3.5']

        where:
        db         | container
        'Postgres' | postgres
        'MySQL'    | mysql
        'MariaDB'  | mariadb
        'SQL Server' | sqlServer
    }

    void "a function compared with a fraction keeps the fraction on #db, on a read and in a bulk statement"() {
        given:
        startOn(container)
        seed()

        expect: 'length(name) is 1, 1 and 3 for the three names, and 3.5 is compared as 3.5 and not as 3'
        criteriaOf('length', new Query.GreaterThan('name', 2.5d)).list()*.name == ['3.5']
        criteriaOf('length', new Query.GreaterThan('name', 3.5d)).list().isEmpty()
        criteriaOf('upper', new Query.Equals('name', 3.5d)).list()*.name == ['3.5']

        when:
        int deleted = criteriaOf('upper', new Query.Equals('name', 3.5d)).deleteAll()
        int none = criteriaOf('length', new Query.GreaterThan('name', 3.5d)).deleteAll()
        session.clear()

        then:
        deleted == 1
        none == 0
        WhereQueryFunctionEntity.list()*.name.sort() == ['a', 'b']

        where:
        db         | container
        'Postgres' | postgres
        'MySQL'    | mysql
        'MariaDB'  | mariadb
        'SQL Server' | sqlServer
    }
    void "an empty list and a subquery without projection work on #db, on a read and in a bulk update and delete"() {
        given:
        startOn(container)
        seed()
        DetachedCriteria<WhereQueryFunctionEntity> named = new DetachedCriteria<>(WhereQueryFunctionEntity).build { eq('name', 'a') }
        def outcome = { Closure c ->
            try { c() } catch (Exception e) { "FAILS ${e.class.simpleName}: ${e.message?.take(120)}".toString() }
        }

        expect: 'an empty list matches nothing, whatever the statement'
        criteriaOf('upper', new Query.In('name', [])).list().isEmpty()
        outcome { criteriaOf('upper', new Query.In('name', [])).updateAll(label: 'x') } == 0
        outcome { criteriaOf('upper', new Query.In('name', [])).deleteAll() } == 0

        and: 'a subquery without projection compares the property, as on a read'
        criteriaOf('lower', new Query.Equals('name', named)).list()*.name == ['a']
        outcome { criteriaOf('lower', new Query.Equals('name', named)).updateAll(label: 'x') } == 1
        outcome { criteriaOf('lower', new Query.Equals('name', named)).deleteAll() } == 1

        where:
        db         | container
        'Postgres' | postgres
        'MySQL'    | mysql
        'MariaDB'  | mariadb
        'SQL Server' | sqlServer
    }

    @IgnoreIf({ data.db == 'DB2' && System.getProperty('os.arch') in ['aarch64', 'arm64'] })
    void "an empty list in a function comparison matches nothing on #db, on a read and in a bulk update and delete"() {
        given:
        if (!container.isRunning()) {
            container.start()
        }
        startOn(container)
        seed()

        expect:
        criteriaOf('upper', new Query.In('name', [])).list().isEmpty()
        criteriaOf('length', new Query.In('name', [])).list().isEmpty()
        criteriaOf('upper', new Query.In('name', [])).updateAll(label: 'x') == 0
        criteriaOf('upper', new Query.In('name', [])).deleteAll() == 0

        where:
        db       | container
        'Oracle' | oracle
        'DB2'    | db2
    }
}
