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
import org.grails.orm.hibernate.dialect.SupportedDatabase
import org.hibernate.engine.spi.SessionFactoryImplementor
import spock.lang.IgnoreIf
import spock.lang.Shared

/**
 * The H2 rows run everywhere; a row that starts a container is skipped when no Docker daemon answers the client that starts
 * it, and the Db2 rows are skipped on arm64 machines, as IBM publishes the image for amd64 only.
 */
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

    @IgnoreIf({ data.container != null && (!DatabaseContainers.isDockerAvailable() || (data.db == 'DB2' && System.getProperty('os.arch') in ['aarch64', 'arm64'])) })
    void "test rlike works with #db"() {
        given:
        useDatabase(container, 'rlikeDB', dialect)
        assertResolved(database)
        new RlikeFoo(name: "ABC").save(flush: true)
        new RlikeFoo(name: "ABCDEF").save(flush: true)
        new RlikeFoo(name: "ABCDEFGHI").save(flush: true)

        when:
        manager.session.clear()
        List<RlikeFoo> allFoos = RlikeFoo.findAllByNameRlike("ABCD.*")

        then: 'the pattern matches two of the three names'
        allFoos.size() == 2

        where:
        db                             | container | dialect                                       | database
        'H2'                           | null      | null                                          | SupportedDatabase.H2
        'Postgres'                     | postgres  | null                                          | SupportedDatabase.POSTGRESQL
        'MySQL'                        | mysql     | null                                          | SupportedDatabase.MYSQL
        'MariaDB'                      | mariadb   | null                                          | SupportedDatabase.MARIADB
        'Oracle'                       | oracle    | null                                          | SupportedDatabase.ORACLE
        'DB2'                          | db2       | null                                          | SupportedDatabase.DB2
        'CockroachDB'                  | cockroach | null                                          | SupportedDatabase.POSTGRESQL
        'CockroachDB with its dialect' | cockroach | 'org.hibernate.dialect.CockroachDB192Dialect' | SupportedDatabase.COCKROACH
    }

    @IgnoreIf({ data.container != null && (!DatabaseContainers.isDockerAvailable() || (data.db == 'DB2' && System.getProperty('os.arch') in ['aarch64', 'arm64'])) })
    void "test rlike on #db matches #expected of Alice, alice and bob for the pattern ^al.*"() {
        given:
        useDatabase(container, 'rlikeCaseDB', dialect)
        assertResolved(database)
        ['Alice', 'alice', 'bob'].each { new RlikeFoo(name: it).save(flush: true) }

        when: 'the pattern is matched'
        manager.session.clear()
        List<String> names = RlikeFoo.findAllByNameRlike('^al.*')*.name.sort()

        then: 'whether case matters follows the database: PostgreSQL matches with ~*, which is case-insensitive (Hibernate 7 renders ~, see #16882), and MySQL and MariaDB follow the collation of the column'
        names == expected

        where:
        db                             | container | dialect                                       | database                      || expected
        'H2'                           | null      | null                                          | SupportedDatabase.H2          || ['alice']
        'Postgres'                     | postgres  | null                                          | SupportedDatabase.POSTGRESQL  || ['Alice', 'alice']
        'MySQL'                        | mysql     | null                                          | SupportedDatabase.MYSQL       || ['Alice', 'alice']
        'MariaDB'                      | mariadb   | null                                          | SupportedDatabase.MARIADB     || ['Alice', 'alice']
        'Oracle'                       | oracle    | null                                          | SupportedDatabase.ORACLE      || ['alice']
        'DB2'                          | db2       | null                                          | SupportedDatabase.DB2         || ['alice']
        'CockroachDB'                  | cockroach | null                                          | SupportedDatabase.POSTGRESQL  || ['Alice', 'alice']
        'CockroachDB with its dialect' | cockroach | 'org.hibernate.dialect.CockroachDB192Dialect' | SupportedDatabase.COCKROACH   || ['Alice', 'alice']
    }

    /**
     * Both CockroachDB rows render the same ~* as PostgreSQL, so their result cannot show whether the dialect that was set
     * is the one in use: check which database the session factory resolved. Hibernate detects CockroachDB as PostgreSQL
     * unless its own dialect is configured.
     */
    private void assertResolved(SupportedDatabase database) {
        def dialect = ((SessionFactoryImplementor) manager.sessionFactory).jdbcServices.dialect
        assert SupportedDatabase.forDialect(dialect) == database
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
