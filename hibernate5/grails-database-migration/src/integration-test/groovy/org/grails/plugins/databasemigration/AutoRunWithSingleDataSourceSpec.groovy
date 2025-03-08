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
@ActiveProfiles('single-datasource')
class AutoRunWithSingleDataSourceSpec extends Specification {

    @Autowired
    DataSource dataSource

    @AutoCleanup
    Sql sql

    def setup() {
        sql = new Sql(dataSource)
        //sql.executeUpdate("drop table AUTHOR")
    }

    def "runs app with a single datasource"() {
        expect:
            def changeSetIds = sql.rows('SELECT id FROM databasechangelog').collect { it.id }
            changeSetIds as Set == ['1', '2', '3', '5'] as Set

        and:
            def authors = sql.rows('SELECT name FROM author').collect { it.name }
            authors == ['Amelia']
    }
}
