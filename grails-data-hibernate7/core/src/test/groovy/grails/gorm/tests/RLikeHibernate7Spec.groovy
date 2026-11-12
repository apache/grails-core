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
import org.testcontainers.spock.Testcontainers
import spock.lang.IgnoreIf
import spock.lang.Requires
import spock.lang.Shared
import spock.lang.Unroll


@Testcontainers
@Requires({ isDockerAvailable() })
class RLikeHibernate7Spec extends HibernateGormDatastoreSpec {

    @Shared postgres = DatabaseContainers.POSTGRES
    @Shared mysql = DatabaseContainers.MYSQL
    @Shared mariadb = DatabaseContainers.MARIADB
    @Shared oracle = DatabaseContainers.ORACLE
    @Shared db2 = DatabaseContainers.DB2
    @Shared sqlServer = DatabaseContainers.SQL_SERVER
    @Shared cockroach = DatabaseContainers.COCKROACH

    void setupSpec() {
        manager.registerDomainClasses(RlikeFoo)
    }

    @IgnoreIf({ data.db == 'DB2' && System.getProperty('os.arch') in ['aarch64', 'arm64'] })
    void "test rlike works with #db"() {
        given:
        if (container != null && !container.isRunning()) {
            container.start()
        }

        // Reconfigure manager for this specific database
        manager.destroy() // Ensure a completely fresh state for each DB
        manager.grailsConfig = [
                'dataSource.url'                     : container?.jdbcUrl ?: "jdbc:h2:mem:rlikeDB;LOCK_TIMEOUT=10000",
                'dataSource.driverClassName'         : container?.driverClassName ?: "org.h2.Driver",
                'dataSource.username'                : container?.username ?: "sa",
                'dataSource.password'                : container?.password ?: "",
                'dataSource.dbCreate'                : 'create-drop',
                'hibernate.show_sql'                 : 'true',
                'hibernate.format_sql'               : 'true',
                'hibernate.highlight_sql'            : 'true',
                'hibernate.id.new_generator_mappings': 'true'
        ]
        // Note: 'hibernate.dialect' is intentionally omitted here.
        // Hibernate 7 is capable of auto-detecting the dialect from JDBC metadata,
        // which avoids deprecation warnings and hardcoded dialect strings.
        
        manager.setup(this.class)

        // Seed data
        new RlikeFoo(name: "ABC").save()
        new RlikeFoo(name: "ABCDEF").save()
        new RlikeFoo(name: "ABCDEFGHI").save(flush: true)

        when:
        manager.session.clear()
        List<RlikeFoo> allFoos = RlikeFoo.findAllByNameRlike("ABCD.*")

        then: 'SQL Server and CockroachDB have no regular expression operator Grails uses, so rlike is a like and the pattern matches nothing'
        allFoos.size() == expected

        where:
        db           | container  || expected
        "H2"         | null       || 2
        "Postgres"   | postgres   || 2
        "MySQL"      | mysql      || 2
        "MariaDB"    | mariadb    || 2
        "Oracle"     | oracle     || 2
        "DB2"        | db2        || 2
        "SQL Server" | sqlServer  || 0
        "CockroachDB"| cockroach  || 0
    }

    @IgnoreIf({ data.db == 'DB2' && System.getProperty('os.arch') in ['aarch64', 'arm64'] })
    void "test rlike on #db matches #expected of Alice, alice and bob for the pattern ^al.*"() {
        given:
        if (container != null && !container.isRunning()) {
            container.start()
        }
        manager.destroy()
        manager.grailsConfig = [
                'dataSource.url'                     : container?.jdbcUrl ?: "jdbc:h2:mem:rlikeCaseDB;LOCK_TIMEOUT=10000",
                'dataSource.driverClassName'         : container?.driverClassName ?: "org.h2.Driver",
                'dataSource.username'                : container?.username ?: "sa",
                'dataSource.password'                : container?.password ?: "",
                'dataSource.dbCreate'                : 'create-drop',
                'hibernate.id.new_generator_mappings': 'true'
        ]
        manager.setup(this.class)
        ['Alice', 'alice', 'bob'].each { new RlikeFoo(name: it).save() }
        RlikeFoo.withSession { it.flush() }

        when: 'the pattern is matched'
        manager.session.clear()
        List<String> names = RlikeFoo.findAllByNameRlike('^al.*')*.name.sort()

        then: 'whether case matters follows the database: PostgreSQL matches with ~, which is case-sensitive, and MySQL and MariaDB follow the collation (Hibernate 5 renders ~*, see #16882)'
        names == expected

        where:
        db           | container || expected
        "H2"         | null      || ['alice']
        "Postgres"   | postgres  || ['alice']
        "MySQL"      | mysql     || ['Alice', 'alice']
        "MariaDB"    | mariadb   || ['Alice', 'alice']
        "Oracle"     | oracle    || ['alice']
        "DB2"        | db2       || ['alice']
        "SQL Server" | sqlServer || []
        "CockroachDB"| cockroach || []
    }
}

@Entity
class RlikeFoo {
    String name
    static mapping = {
        id generator: 'identity'
    }
}
