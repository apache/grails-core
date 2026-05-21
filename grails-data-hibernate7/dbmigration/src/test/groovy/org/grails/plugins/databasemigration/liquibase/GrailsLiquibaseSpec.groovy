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
package org.grails.plugins.databasemigration.liquibase

import java.sql.Connection

import liquibase.Contexts
import liquibase.LabelExpression
import liquibase.Liquibase
import liquibase.database.Database
import liquibase.resource.ResourceAccessor

import org.springframework.context.ApplicationContext
import spock.lang.Specification

class GrailsLiquibaseSpec extends Specification {

    ApplicationContext applicationContext = Mock()
    GrailsLiquibase grailsLiquibase

    def setup() {
        grailsLiquibase = new GrailsLiquibase(applicationContext)
    }

    def "performUpdate invokes callbacks if they exist"() {
        given:
        Liquibase liquibase = Mock()
        Database database = Mock()
        liquibase.database >> database
        
        def callbacks = Mock(TestCallbacks)
        
        applicationContext.containsBean('migrationCallbacks') >> true
        applicationContext.getBean('migrationCallbacks') >> callbacks
        
        grailsLiquibase.changeLog = "test.xml"

        when:
        grailsLiquibase.performUpdate(liquibase)

        then:
        1 * callbacks.beforeStartMigration(database)
        1 * callbacks.onStartMigration(database, liquibase, "test.xml")
        1 * liquibase.update(_ as Contexts, _ as LabelExpression)
        1 * callbacks.afterMigrations(database)
    }

    def "performUpdate proceeds normally if no callbacks"() {
        given:
        Liquibase liquibase = Mock()
        Database database = Mock()
        liquibase.database >> database
        
        applicationContext.containsBean('migrationCallbacks') >> false

        when:
        grailsLiquibase.performUpdate(liquibase)

        then:
        1 * liquibase.update(_ as Contexts, _ as LabelExpression)
    }
    
    interface TestCallbacks {
        void beforeStartMigration(Database db)
        void onStartMigration(Database db, Liquibase liq, String log)
        void afterMigrations(Database db)
    }
}
