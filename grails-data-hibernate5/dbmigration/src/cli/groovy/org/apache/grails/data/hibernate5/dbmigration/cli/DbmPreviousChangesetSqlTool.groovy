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
package org.apache.grails.data.hibernate5.dbmigration.cli

import groovy.transform.CompileStatic

import liquibase.Liquibase
import liquibase.database.Database

import org.apache.grails.core.cli.ApplicationTool
import org.apache.grails.core.cli.ToolHints
import org.apache.grails.core.cli.ToolOption
import org.apache.grails.core.cli.ToolParameter
import org.grails.plugins.databasemigration.DatabaseMigrationException

@ToolHints(readOnlyHint = false, destructiveHint = true)
@DatabaseMigrationOptions
@ToolOption(names = ['--skip'], arity = '1', label = 'NUMBER', defaultValue = '0',
        description = 'The number of the last change sets of the change log to leave out')
@ToolParameter(index = 0, label = 'NUMBER', arity = '1', description = 'The number of change sets, from the last of the change log, whose SQL is written')
@ToolParameter(index = 1, label = 'FILE', arity = '1', required = false,
        description = 'The file to write the SQL to; the standard output when not given')
@CompileStatic
class DbmPreviousChangesetSqlTool implements ApplicationTool, ApplicationContextDatabaseMigrationCommand {

    final String description = 'Generates the SQL to apply the previous <value> change sets'

    @Override
    void handle() {

        String count = args[0]
        if (!count) {
            throw new DatabaseMigrationException("The $name command requires a change set number argument")
        }
        if (!count.isNumber()) {
            throw new DatabaseMigrationException("The change set number argument '$count' isn't a number")
        }

        def filename = args[1]

        String skip = optionValue('skip') ?: '0'

        if (!skip.isNumber()) {
            throw new DatabaseMigrationException("The change set skip argument '$skip' isn't a number")
        }

        configureLiquibase()

        withLiquibase { Liquibase liquibase ->
            withDatabase { Database database ->
                withFileOrSystemOutWriter(filename) { Writer output ->
                    doGeneratePreviousChangesetSql(output, database, liquibase, count, skip)
                }
            }
        }
    }
}
