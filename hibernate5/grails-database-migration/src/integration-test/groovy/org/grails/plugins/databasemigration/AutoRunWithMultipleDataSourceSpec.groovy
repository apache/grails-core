/*
 * Copyright 2015 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.plugins.databasemigration

import grails.testing.mixin.integration.Integration
import groovy.sql.Sql
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ActiveProfiles
import spock.lang.AutoCleanup
import spock.lang.Specification

import javax.sql.DataSource

@Integration
@ActiveProfiles('multiple-datasource')
class AutoRunWithMultipleDataSourceSpec extends Specification {

    @Autowired
    DataSource dataSource

    @Autowired
    DataSource dataSource_second

    @AutoCleanup
    Sql sql

    @AutoCleanup
    Sql secondSql

    def setup() {
        sql = new Sql(dataSource)
        secondSql = new Sql(dataSource_second)
    }

    def "runs app with a multiple datasource"() {
        when:
            def changeSetIds = sql.rows('SELECT id FROM DATABASECHANGELOG').collect { it.id }

        then:
            changeSetIds as Set == ['1', '2', '3', '4', '5'] as Set

        when:
            def secondChangeSetIds = secondSql.rows('SELECT id FROM DATABASECHANGELOG').collect { it.id }

        then:
            secondChangeSetIds as Set == ['second-1', 'second-2', 'second-3'] as Set
    }
}
