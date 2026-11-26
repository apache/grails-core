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
package org.grails.orm.hibernate

import grails.gorm.annotation.Entity
import grails.gorm.hibernate.HibernateEntity
import grails.gorm.tests.HibernateGormDatastoreSpec
import org.testcontainers.mariadb.MariaDBContainer
import org.testcontainers.mysql.MySQLContainer
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.spock.Testcontainers
import spock.lang.Issue
import spock.lang.Requires
import spock.lang.Shared

/**
 * Pins https://github.com/apache/grails-core/issues/15276 on every externally-run dialect (see
 * {@link GormTextTypeColumnIntegrationSpec} for the same precedent): an instance saved with
 * {@code validate: false} is persisted by the flush instead of being validated again, and does not
 * stop a later valid save from flushing. The reporter used PostgreSQL. H2 coverage lives in
 * {@link SaveWithoutValidationSpec}, which needs no container.
 */
@Issue('https://github.com/apache/grails-core/issues/15276')
@Testcontainers
@Requires({ isDockerAvailable() })
class SaveWithoutValidationDialectSpec extends HibernateGormDatastoreSpec {

    @Shared PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16")
    @Shared MySQLContainer mysql = new MySQLContainer("mysql:8.0")
    @Shared MariaDBContainer mariadb = new MariaDBContainer("mariadb:10.11")

    void setupSpec() {
        manager.registerDomainClasses(UnvalidatedNote)
    }

    void "an invalid instance saved with validate false is persisted on #db"() {
        given:
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

        when:
        new UnvalidatedNote(title: '').save(validate: false, flush: true)
        session.clear()

        then: 'the flush does not validate the instance again'
        UnvalidatedNote.count() == 1
        UnvalidatedNote.list().first().title == ''

        when: 'a later valid save flushes while the invalid instance is still managed'
        UnvalidatedNote kept = UnvalidatedNote.list().first()
        kept.body = 'edited'
        new UnvalidatedNote(title: 'valid').save(failOnError: true, flush: true)

        then:
        UnvalidatedNote.count() == 2

        where:
        db          | container
        "Postgres"  | postgres
        "MySQL"     | mysql
        "MariaDB"   | mariadb
    }
}

@Entity
class UnvalidatedNote implements HibernateEntity<UnvalidatedNote> {
    String title
    String body

    static constraints = {
        title blank: false
        body nullable: true
    }
}
