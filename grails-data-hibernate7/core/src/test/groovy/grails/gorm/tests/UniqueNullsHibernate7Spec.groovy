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

import java.time.Duration

import org.springframework.dao.DataIntegrityViolationException
import org.testcontainers.mariadb.MariaDBContainer
import org.testcontainers.mysql.MySQLContainer
import org.testcontainers.oracle.OracleContainer
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.spock.Testcontainers
import spock.lang.Requires
import spock.lang.Shared
import spock.lang.Unroll

import grails.gorm.annotation.Entity
import org.grails.datastore.mapping.config.UniqueNulls

/**
 * The {@code unique} constraint reads a null in a composite key the way the unique index of the database does, unless it
 * is told otherwise: on every database the validation of a key whose group is null agrees with whether the database
 * accepts the same row twice.
 */
@Testcontainers
@Requires({ isDockerAvailable() })
class UniqueNullsHibernate7Spec extends HibernateGormDatastoreSpec {

    @Shared postgres = new PostgreSQLContainer('postgres:16')
    @Shared mysql = new MySQLContainer('mysql:8.0')
    @Shared mariadb = new MariaDBContainer('mariadb:10.11')
    @Shared oracle = new OracleContainer('gvenzl/oracle-free:slim-faststart')
            .withStartupTimeout(Duration.ofMinutes(3))

    void setupSpec() {
        manager.registerDomainClasses(UnqLanguage, UnqLanguageValue)
    }

    @Unroll
    void 'on #db the validation reads nulls as #reading, as the unique index does'() {
        given: 'the datastore on this database, with the schema created for it'
        if (container != null && !container.isRunning()) {
            container.start()
        }
        manager.destroy()
        manager.grailsConfig = [
                'dataSource.url'            : container?.jdbcUrl ?: "jdbc:h2:mem:uniqueNulls${db};LOCK_TIMEOUT=10000".toString(),
                'dataSource.driverClassName': container?.driverClassName ?: 'org.h2.Driver',
                'dataSource.username'       : container?.username ?: 'sa',
                'dataSource.password'       : container?.password ?: '',
                'dataSource.dbCreate'       : 'create-drop'
        ]
        manager.setup(this.class)
        UnqLanguage.withNewTransaction {
            new UnqLanguage(language: 'en', country: null).save(validate: false, flush: true)
        }

        when: 'the same key is validated'
        boolean validationRejects = UnqLanguage.withNewSession {
            UnqLanguage.withNewTransaction {
                !new UnqLanguage(language: 'en', country: null).validate()
            }
        }

        and: 'and the database is asked to take the same row twice'
        boolean indexRejects = false
        try {
            UnqLanguage.withNewTransaction {
                new UnqLanguage(language: 'en', country: null).save(validate: false, flush: true)
            }
        } catch (DataIntegrityViolationException ignored) {
            indexRejects = true
        }

        then: 'the datastore says what the database does, and the validation agrees with the index'
        manager.session.datastore.databaseUniqueNulls == reading
        manager.session.datastore.uniqueNulls == reading
        validationRejects == indexRejects
        indexRejects == (reading == UniqueNulls.VALUE)

        where:
        db         | container || reading
        'H2'       | null      || UniqueNulls.SKIP
        'Postgres' | postgres  || UniqueNulls.SKIP
        'MySQL'    | mysql     || UniqueNulls.SKIP
        'MariaDB'  | mariadb   || UniqueNulls.SKIP
        'Oracle'   | oracle    || UniqueNulls.VALUE
    }

    @Unroll
    void 'the setting #setting overrides the database on H2, and a constraint option overrides the setting'() {
        given:
        manager.destroy()
        Map config = [
                'dataSource.url'            : 'jdbc:h2:mem:uniqueNullsSetting;LOCK_TIMEOUT=10000',
                'dataSource.driverClassName': 'org.h2.Driver',
                'dataSource.username'       : 'sa',
                'dataSource.password'       : '',
                'dataSource.dbCreate'       : 'create-drop'
        ]
        if (setting != null) {
            config['grails.gorm.unique.nulls'] = setting
        }
        manager.grailsConfig = config
        manager.setup(this.class)
        UnqLanguage.withNewTransaction {
            new UnqLanguage(language: 'en', country: null).save(validate: false, flush: true)
            new UnqLanguageValue(language: 'en', country: null).save(validate: false, flush: true)
        }

        expect: 'the database stays what H2 is, and the setting decides the constraint without an option'
        manager.session.datastore.databaseUniqueNulls == UniqueNulls.SKIP
        manager.session.datastore.uniqueNulls == reading
        UnqLanguage.withNewSession { UnqLanguage.withNewTransaction { !new UnqLanguage(language: 'en', country: null).validate() } } == duplicate

        and: 'a constraint that asks for value keeps it whatever the setting says'
        UnqLanguageValue.withNewSession { UnqLanguageValue.withNewTransaction { !new UnqLanguageValue(language: 'en', country: null).validate() } }

        where:
        setting    || reading             | duplicate
        null       || UniqueNulls.SKIP     | false
        'database' || UniqueNulls.SKIP     | false
        'skip'     || UniqueNulls.SKIP     | false
        'value'    || UniqueNulls.VALUE    | true
        'wildcard' || UniqueNulls.WILDCARD | true
    }
}

@Entity
class UnqLanguage {

    String language
    String country

    static constraints = {
        language(nullable: true, unique: 'country')
        country(nullable: true)
    }

    static mapping = {
        version false
        language unique: 'country'
    }
}

@Entity
class UnqLanguageValue {

    String language
    String country

    static constraints = {
        language(nullable: true, unique: [group: 'country', nulls: 'value'])
        country(nullable: true)
    }

    static mapping = {
        version false
    }
}
