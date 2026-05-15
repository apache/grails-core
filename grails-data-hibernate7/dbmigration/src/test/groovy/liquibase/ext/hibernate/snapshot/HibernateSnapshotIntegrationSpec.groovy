/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  'License'); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package liquibase.ext.hibernate.snapshot

import liquibase.CatalogAndSchema
import liquibase.ext.hibernate.database.HibernateDatabase
import liquibase.snapshot.DatabaseSnapshot
import liquibase.snapshot.JdbcDatabaseSnapshot
import liquibase.structure.DatabaseObject
import org.grails.orm.hibernate.HibernateDatastore
import org.grails.plugins.databasemigration.liquibase.GormDatabase
import org.hibernate.boot.Metadata
import org.hibernate.dialect.PostgreSQLDialect
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.spock.Testcontainers
import spock.lang.AutoCleanup
import spock.lang.Requires
import spock.lang.Shared
import spock.lang.Specification

@Testcontainers
@Requires({ isDockerAvailable() })
abstract class HibernateSnapshotIntegrationSpec extends Specification {

    @Shared PostgreSQLContainer postgres = new PostgreSQLContainer('postgres:16')

    @AutoCleanup
    HibernateDatastore datastore
    HibernateDatabase database
    DatabaseSnapshot snapshot
    Metadata metadata

    def setup() {
        Map config = [
                'hibernate.dialect'           : PostgreSQLDialect.class.getName(),
                'dataSource.url'              : postgres.jdbcUrl,
                'dataSource.driverClassName'  : postgres.driverClassName,
                'dataSource.username'         : postgres.username,
                'dataSource.password'         : postgres.password,
                'hibernate.hbm2ddl.auto'      : 'create-drop',
                'hibernate.integration.envers.enabled': false
        ]
        
        datastore = new HibernateDatastore(config, getEntityClasses() as Class[])
        metadata = datastore.getMetadata()
        
        database = new GormDatabase(new PostgreSQLDialect(), datastore)
        
        snapshot = new JdbcDatabaseSnapshot([] as DatabaseObject[], database)
    }

    abstract List<Class> getEntityClasses()

    /**
     * Returns true when a Docker daemon is reachable on this machine.
     */
    static boolean isDockerAvailable() {
        def candidates = [
            System.getProperty('user.home') + '/.docker/run/docker.sock',
            '/var/run/docker.sock',
            System.getenv('DOCKER_HOST') ?: ''
        ]
        candidates.any { it && new File(it).exists() }
    }
}
