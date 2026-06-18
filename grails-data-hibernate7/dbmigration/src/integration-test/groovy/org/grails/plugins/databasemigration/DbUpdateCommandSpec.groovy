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

package org.grails.plugins.databasemigration

import grails.dev.commands.ApplicationCommand
import grails.dev.commands.ExecutionContext
import grails.testing.mixin.integration.Integration
import grails.util.GrailsNameUtils
import groovy.sql.Sql
import liquibase.GlobalConfiguration
import liquibase.Scope
import liquibase.exception.LiquibaseException
import org.grails.build.parsing.CommandLineParser
import org.grails.plugins.databasemigration.command.DbmUpdateCommand
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component
import org.springframework.test.context.ActiveProfiles
import spock.lang.AutoCleanup
import spock.lang.Specification

import javax.sql.DataSource

@Integration
@ActiveProfiles('transaction-datasource')
@Component
class DbUpdateCommandSpec extends Specification {

    @Autowired
    DataSource dataSource

    @Autowired
    ApplicationContext applicationContext

    @AutoCleanup
    Sql sql

    def setup() {
        sql = new Sql(dataSource)
    }

    void "test the transaction behaviour in the changeSet with grailsChange and GORM"() {

        when:
        Scope.child(GlobalConfiguration.DUPLICATE_FILE_MODE.getKey(), GlobalConfiguration.DuplicateFileMode.WARN, { ->
            DbmUpdateCommand command = new DbmUpdateCommand()
            command.applicationContext = applicationContext
            command.setExecutionContext(getExecutionContext(DbmUpdateCommand))
            command.handle()
        } as Scope.ScopedRunner)

        then:
        def e = thrown(LiquibaseException)
        e.cause instanceof LiquibaseException
        sql.firstRow('SELECT COUNT(*) AS num FROM DATABASECHANGELOG WHERE id=?;', 'create-person-grails').num == 1
        sql.firstRow('SELECT COUNT(*) AS num FROM person;').num == 1
        sql.firstRow('SELECT COUNT(*) AS num FROM account;').num == 0

    }

    private ExecutionContext getExecutionContext(Class<ApplicationCommand> clazz, String... args) {
        def commandClassName = GrailsNameUtils.getScriptName(GrailsNameUtils.getLogicalName(clazz.name, 'Command'))
        new ExecutionContext(
                new CommandLineParser().parse(([commandClassName] + args.toList()) as String[])
        )
    }
}




