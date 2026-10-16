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
import jakarta.persistence.GenerationType
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.orm.hibernate.HibernateDatastore
import org.hibernate.engine.jdbc.spi.JdbcServices
import org.hibernate.internal.SessionFactoryImpl
import org.testcontainers.mariadb.MariaDBContainer
import org.testcontainers.mysql.MySQLContainer
import org.testcontainers.oracle.OracleContainer
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.spock.Testcontainers
import spock.lang.Requires
import spock.lang.Shared
import spock.lang.Specification

import java.sql.Connection
import java.sql.ResultSet
import java.sql.Statement
import java.time.Duration

/**
 * Boots a datastore with {@code dbCreate: create-drop} and the default id mapping on every database that
 * {@link RLikeHibernate7Spec} covers. The default {@code native} generator picks IDENTITY on some of them and a
 * sequence on others; on the sequence based ones the sequence has to be created by the schema export or no entity
 * can be saved.
 * <p>
 * It deliberately does not extend {@link HibernateGormDatastoreSpec}: that base class configures
 * {@code grails.gorm.default.mapping} with {@code id generator: 'identity'}, which would hide the default mapping.
 */
@Testcontainers
@Requires({ HibernateGormDatastoreSpec.isDockerAvailable() })
class NativeIdGeneratorHibernate7Spec extends Specification {

    @Shared postgres = new PostgreSQLContainer("postgres:16")
    @Shared mysql = new MySQLContainer("mysql:8.0")
    @Shared mariadb = new MariaDBContainer("mariadb:10.11")
    @Shared oracle = new OracleContainer("gvenzl/oracle-free:slim-faststart")
            .withStartupTimeout(Duration.ofMinutes(3))

    void "default id mapping generates distinct ids and creates the sequence it needs with #db"() {
        given:
        if (container != null && !container.isRunning()) {
            container.start()
        }
        Map config = [
                'dataSource.url'            : container?.jdbcUrl ?: "jdbc:h2:mem:nativeIdDB;LOCK_TIMEOUT=10000",
                'dataSource.driverClassName': container?.driverClassName ?: "org.h2.Driver",
                'dataSource.username'       : container?.username ?: "sa",
                'dataSource.password'       : container?.password ?: "",
                'dataSource.dbCreate'       : 'create-drop'
        ]
        HibernateDatastore datastore = new HibernateDatastore(DatastoreUtils.createPropertyResolver(config), NativeIdBook)

        when:
        def ids = []
        NativeIdBook.withTransaction {
            ids << new NativeIdBook(title: 'first').save(flush: true).id
            ids << new NativeIdBook(title: 'second').save(flush: true).id
        }
        def titles = NativeIdBook.withNewSession { ids.collect { NativeIdBook.get(it).title } }

        then:
        ids.every { it != null }
        ids.unique().size() == 2
        titles == ['first', 'second']

        and: "a dialect whose native strategy is a sequence got that sequence from the schema export"
        !usesSequence(datastore) || sequenceNames(datastore).any { it.toLowerCase().contains('native_id_book') }

        cleanup:
        datastore?.destroy()

        where:
        db         | container
        "H2"       | null
        "Postgres" | postgres
        "MySQL"    | mysql
        "MariaDB"  | mariadb
        "Oracle"   | oracle
    }

    private static boolean usesSequence(HibernateDatastore datastore) {
        GenerationType strategy = ((SessionFactoryImpl) datastore.sessionFactory)
                .serviceRegistry.getService(JdbcServices).dialect.nativeValueGenerationStrategy
        strategy == GenerationType.SEQUENCE || strategy == GenerationType.AUTO
    }

    private static List<String> sequenceNames(HibernateDatastore datastore) {
        List<String> names = []
        datastore.connectionSources.defaultConnectionSource.dataSource.connection.withCloseable { Connection c ->
            // Oracle's driver does not report sequences through DatabaseMetaData.getTables
            if (c.metaData.databaseProductName == 'Oracle') {
                c.createStatement().withCloseable { Statement st ->
                    st.executeQuery('select sequence_name from user_sequences').withCloseable { ResultSet rs ->
                        while (rs.next()) {
                            names << rs.getString(1)
                        }
                    }
                }
            } else {
                c.metaData.getTables(null, null, '%', ['SEQUENCE'] as String[]).withCloseable { ResultSet rs ->
                    while (rs.next()) {
                        names << rs.getString('TABLE_NAME')
                    }
                }
            }
        }
        names
    }
}

@Entity
class NativeIdBook {
    String title
}
