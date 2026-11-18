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
import org.apache.grails.data.hibernate5.core.GrailsDataHibernate5TckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.testcontainers.spock.Testcontainers
import spock.lang.IgnoreIf
import spock.lang.Requires
import spock.lang.Shared

@Testcontainers
@Requires({ DatabaseContainers.isDockerAvailable() })
class RLikeSpec extends GrailsDataTckSpec<GrailsDataHibernate5TckManager> {

    @Shared postgres = DatabaseContainers.POSTGRES
    @Shared mysql = DatabaseContainers.MYSQL
    @Shared mariadb = DatabaseContainers.MARIADB
    @Shared oracle = DatabaseContainers.ORACLE
    @Shared db2 = DatabaseContainers.DB2
    @Shared cockroach = DatabaseContainers.COCKROACH

    void setupSpec() {
        manager.registerDomainClasses(RlikeFoo)
    }

    @IgnoreIf({ data.db == 'DB2' && System.getProperty('os.arch') in ['aarch64', 'arm64'] })
    void "test rlike works with #db"() {
        given:
        useDatabase(container, 'rlikeDB', dialect)
        new RlikeFoo(name: "ABC").save(flush: true)
        new RlikeFoo(name: "ABCDEF").save(flush: true)
        new RlikeFoo(name: "ABCDEFGHI").save(flush: true)

        when:
        manager.session.clear()
        List<RlikeFoo> allFoos = RlikeFoo.findAllByNameRlike("ABCD.*")

        then: 'CockroachDB matches with the operator of PostgreSQL, whether Hibernate detects it as PostgreSQL or its own dialect is set'
        allFoos.size() == expected

        where:
        db                             | container | dialect                                       || expected
        'H2'                           | null      | null                                          || 2
        'Postgres'                     | postgres  | null                                          || 2
        'MySQL'                        | mysql     | null                                          || 2
        'MariaDB'                      | mariadb   | null                                          || 2
        'Oracle'                       | oracle    | null                                          || 2
        'DB2'                          | db2       | null                                          || 2
        'CockroachDB'                  | cockroach | null                                          || 2
        'CockroachDB with its dialect' | cockroach | 'org.hibernate.dialect.CockroachDB192Dialect' || 2
    }

    @IgnoreIf({ data.db == 'DB2' && System.getProperty('os.arch') in ['aarch64', 'arm64'] })
    void "test rlike on #db matches #expected of Alice, alice and bob for the pattern ^al.*"() {
        given:
        useDatabase(container, 'rlikeCaseDB', dialect)
        ['Alice', 'alice', 'bob'].each { new RlikeFoo(name: it).save(flush: true) }

        when: 'the pattern is matched'
        manager.session.clear()
        List<String> names = RlikeFoo.findAllByNameRlike('^al.*')*.name.sort()

        then: 'whether case matters follows the database: PostgreSQL matches with ~*, which is case-insensitive (Hibernate 7 renders ~, see #16882), and MySQL and MariaDB follow the collation of the column'
        names == expected

        where:
        db                             | container | dialect                                       || expected
        'H2'                           | null      | null                                          || ['alice']
        'Postgres'                     | postgres  | null                                          || ['Alice', 'alice']
        'MySQL'                        | mysql     | null                                          || ['Alice', 'alice']
        'MariaDB'                      | mariadb   | null                                          || ['Alice', 'alice']
        'Oracle'                       | oracle    | null                                          || ['alice']
        'DB2'                          | db2       | null                                          || ['alice']
        'CockroachDB'                  | cockroach | null                                          || ['Alice', 'alice']
        'CockroachDB with its dialect' | cockroach | 'org.hibernate.dialect.CockroachDB192Dialect' || ['Alice', 'alice']
    }

    private void useDatabase(container, String h2Name, String dialect) {
        if (container != null && !container.isRunning()) {
            container.start()
        }
        manager.destroy()
        manager.grailsConfig = new ConfigObject()
        manager.grailsConfig.dataSource.url = container?.jdbcUrl ?: "jdbc:h2:mem:${h2Name};LOCK_TIMEOUT=10000".toString()
        manager.grailsConfig.dataSource.driverClassName = container?.driverClassName ?: 'org.h2.Driver'
        manager.grailsConfig.dataSource.username = container?.username ?: 'sa'
        manager.grailsConfig.dataSource.password = container?.password ?: ''
        manager.grailsConfig.dataSource.dbCreate = 'create-drop'
        if (dialect != null) {
            manager.grailsConfig.dataSource.dialect = dialect
        }
        manager.setup(this.class)
    }
}

@Entity
class RlikeFoo {
    String name
}
