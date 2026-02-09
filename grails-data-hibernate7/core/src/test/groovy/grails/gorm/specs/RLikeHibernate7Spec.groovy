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

import org.testcontainers.DockerClientFactory
import org.testcontainers.oracle.OracleContainer
import spock.lang.Requires

import grails.gorm.annotation.Entity
import org.testcontainers.containers.MariaDBContainer
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.spock.Testcontainers
import spock.lang.Shared
import spock.lang.Unroll

@Testcontainers
import org.testcontainers.dockerclient.DockerClientProviderStrategy

// In your Spock @Requires or @IgnoreIf closure:
@Requires({
    try {
        DockerClientFactory.instance().client()
        true
    } catch (ignored) { false }
})
class RLikeHibernate7Spec extends HibernateGormDatastoreSpec {

    @Shared postgres = new PostgreSQLContainer("postgres:16")
    @Shared mysql = new MySQLContainer("mysql:8.0")
    @Shared mariadb = new MariaDBContainer("mariadb:10.11")
    @Shared oracle = new OracleContainer("gvenzl/oracle-free:23-slim")

    void setupSpec() {
        manager.addAllDomainClasses([RlikeFoo])
    }

    void cleanupSpec() {
        // Testcontainers @Testcontainers + @Shared handles stopping
    }

    @Unroll
    void "test rlike works with #db"() {
        given:
        if (container != null && !container.isRunning()) {
            container.start()
        }

        String url = container ? container.jdbcUrl : "jdbc:h2:mem:grailsDB"
        String driver = container ? container.driverClassName : "org.h2.Driver"
        String username = container ? container.username : "sa"
        String password = container ? container.password : ""

        // Reconfigure manager for this specific database
        manager.cleanup() // Clean up previous session/datastore
        manager.grailsConfig = [
                'dataSource.url'           : url,
                'dataSource.driverClassName': driver,
                'dataSource.username'      : username,
                'dataSource.password'      : password,
                'dataSource.dbCreate'      : 'create-drop',
                'hibernate.dialect'        : dialect,
                'hibernate.hbm2ddl.auto'   : 'create',
                'hibernate.show_sql'       : 'true',
                'hibernate.format_sql'     : 'true',
                'hibernate.id.new_generator_mappings': 'true'
        ]
        manager.setup(this.class) // Initialize with new config

        // Use the same given data
        new RlikeFoo(name: "ABC").save()
        new RlikeFoo(name: "ABCDEF").save()
        new RlikeFoo(name: "ABCDEFGHI").save(flush: true)

        when:
        manager.session.clear()
        List<RlikeFoo> allFoos = RlikeFoo.findAllByNameRlike("ABCD.*")

        then:
        allFoos.size() == 2

        where:
        db           | container | dialect
        "H2"         | null      | "org.hibernate.dialect.H2Dialect"
        "Postgres"   | postgres  | "org.hibernate.dialect.PostgreSQLDialect"
        "MySQL"      | mysql     | "org.hibernate.dialect.MySQLDialect"
        "MariaDB"    | mariadb   | "org.hibernate.dialect.MariaDBDialect"
        "Oracle"     | oracle    | "org.hibernate.dialect.OracleDialect"
    }
}

@Entity
class RlikeFoo {
    String name
    static mapping = {
        id generator: 'identity'
    }
}