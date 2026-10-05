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

import java.time.Duration

import org.testcontainers.mariadb.MariaDBContainer
import org.testcontainers.mysql.MySQLContainer
import org.testcontainers.oracle.OracleContainer
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.spock.Testcontainers
import spock.lang.Requires
import spock.lang.Shared

import grails.gorm.annotation.Entity

@Testcontainers
@Requires({ isDockerAvailable() })
class SqlRestrictionHibernate7Spec extends HibernateGormDatastoreSpec {

    @Shared postgres = new PostgreSQLContainer('postgres:16')
    @Shared mysql = new MySQLContainer('mysql:8.0')
    @Shared mariadb = new MariaDBContainer('mariadb:10.11')
    @Shared oracle = new OracleContainer('gvenzl/oracle-free:slim-faststart')
            .withStartupTimeout(Duration.ofMinutes(3))

    void setupSpec() {
        manager.registerDomainClasses(SqlRestrictionParent, SqlRestrictionChild)
    }

    void "sqlRestriction works with #db"() {
        given:
        if (container != null && !container.isRunning()) {
            container.start()
        }
        manager.destroy()
        manager.grailsConfig = [
                'dataSource.url'            : container?.jdbcUrl ?: 'jdbc:h2:mem:sqlRestrictionDB;LOCK_TIMEOUT=10000',
                'dataSource.driverClassName': container?.driverClassName ?: 'org.h2.Driver',
                'dataSource.username'       : container?.username ?: 'sa',
                'dataSource.password'       : container?.password ?: '',
                'dataSource.dbCreate'       : 'create-drop',
                'hibernate.show_sql'        : 'true',
        ]
        manager.setup(this.class)

        new SqlRestrictionParent(name: 'ABC').save()
        new SqlRestrictionParent(name: 'ABCDEF').addToChildren(name: 'ABCDEF-kid').save()
        new SqlRestrictionParent(name: 'ABCDEFGHI').addToChildren(name: 'XYZ-kid').save(flush: true)
        manager.session.clear()

        expect: 'a condition without parameters'
        SqlRestrictionParent.createCriteria().list {
            sqlRestriction('length({alias}.name) <= 4')
        }*.name == ['ABC']

        and: 'a condition with bound parameters'
        SqlRestrictionParent.createCriteria().list {
            sqlRestriction('length({alias}.name) < ? and length({alias}.name) > ?', [7, 3])
        }*.name == ['ABCDEF']

        and: '{alias} picks the queried entity when a join adds another name column'
        SqlRestrictionParent.createCriteria().listDistinct {
            children {
                eq('name', 'ABCDEF-kid')
            }
            sqlRestriction('{alias}.name like ?', ['ABC%'])
        }*.name == ['ABCDEF']

        and: 'a negated condition'
        SqlRestrictionParent.createCriteria().list {
            not {
                sqlRestriction('length({alias}.name) <= 4')
            }
        }*.name.sort() == ['ABCDEF', 'ABCDEFGHI']

        and: 'a condition in a disjunction'
        SqlRestrictionParent.createCriteria().list {
            or {
                sqlRestriction('length({alias}.name) <= 4')
                eq('name', 'ABCDEFGHI')
            }
        }*.name.sort() == ['ABC', 'ABCDEFGHI']

        and: 'a condition with or, grouped as a whole'
        SqlRestrictionParent.createCriteria().list {
            sqlRestriction("{alias}.name = 'ABC' or {alias}.name = 'ABCDEF'")
            eq('name', 'ABCDEF')
        }*.name == ['ABCDEF']

        and: 'a question mark in a string literal or a comment is not a placeholder'
        SqlRestrictionParent.createCriteria().list {
            sqlRestriction("{alias}.name not like '%?' and length({alias}.name) > ? -- why?", [6])
        }*.name == ['ABCDEFGHI']

        and: 'a condition in an association block inside a junction'
        SqlRestrictionParent.createCriteria().listDistinct {
            or {
                children {
                    sqlRestriction('{alias}.name = ?', ['ABCDEF-kid'])
                }
                eq('name', 'ABC')
            }
        }*.name == ['ABCDEF']

        and: 'a count'
        SqlRestrictionParent.createCriteria().count {
            sqlRestriction('length({alias}.name) > ?', [3])
        } == 2

        and: 'a paged list counts the total with the condition'
        SqlRestrictionParent.createCriteria().list(max: 1) {
            sqlRestriction('length({alias}.name) > ?', [3])
        }.totalCount == 2

        where:
        db         | container
        'H2'       | null
        'Postgres' | postgres
        'MySQL'    | mysql
        'MariaDB'  | mariadb
        'Oracle'   | oracle
    }
}

@Entity
class SqlRestrictionParent {

    String name
    static hasMany = [children: SqlRestrictionChild]
    static mapping = {
        id(generator: 'identity')
    }
}

@Entity
class SqlRestrictionChild {

    String name
    static belongsTo = [parent: SqlRestrictionParent]
    static mapping = {
        id(generator: 'identity')
    }
}
