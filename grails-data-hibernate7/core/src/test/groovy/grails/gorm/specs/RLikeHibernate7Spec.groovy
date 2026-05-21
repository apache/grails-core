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
package grails.gorm.specs

import grails.gorm.annotation.Entity
import org.testcontainers.mariadb.MariaDBContainer
import org.testcontainers.mysql.MySQLContainer
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.oracle.OracleContainer
import org.testcontainers.spock.Testcontainers
import spock.lang.Requires
import spock.lang.Shared
import spock.lang.Unroll

@Testcontainers
@Requires({ isDockerAvailable() })
class RLikeHibernate7Spec extends HibernateGormDatastoreSpec {

    @Shared postgres = new PostgreSQLContainer("postgres:16")
    @Shared mysql = new MySQLContainer("mysql:8.0")
    @Shared mariadb = new MariaDBContainer("mariadb:10.11")
    @Shared oracle = new OracleContainer("gvenzl/oracle-free:slim-faststart")

    void setupSpec() {
        manager.addAllDomainClasses([RlikeFoo])
    }

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

        then:
        allFoos.size() == 2

        where:
        db           | container
        "H2"         | null
        "Postgres"   | postgres
        "MySQL"      | mysql
        "MariaDB"    | mariadb
        // "Oracle"     | oracle
    }
}

@Entity
class RlikeFoo {
    String name
    static mapping = {
        id generator: 'identity'
    }
}