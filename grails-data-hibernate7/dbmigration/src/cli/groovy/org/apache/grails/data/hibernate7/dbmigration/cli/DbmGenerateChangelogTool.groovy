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
package org.apache.grails.data.hibernate7.dbmigration.cli

import groovy.transform.CompileStatic

import liquibase.database.Database

import org.apache.grails.core.cli.ApplicationTool
import org.apache.grails.core.cli.ToolHints
import org.apache.grails.core.cli.ToolOption
import org.apache.grails.core.cli.ToolParameter
import org.grails.plugins.databasemigration.DatabaseMigrationException

@ToolHints(readOnlyHint = false, destructiveHint = true)
@DatabaseMigrationOptions
@ToolOption(names = ['--force'], arity = '0', description = 'Replaces the change log when it exists')
@ToolOption(names = ['--add'], arity = '0', description = 'Includes the change log it writes in the root change log')
@ToolOption(names = ['--diffTypes'], arity = '1', label = 'TYPES',
        description = 'Taken so that the command lines that give it still run, and not used: the change log has every type of change')
@ToolParameter(index = 0, label = 'FILE', arity = '1', required = false,
        description = 'The change log to write, in the folder of the change logs; the standard output when not given')
@CompileStatic
class DbmGenerateChangelogTool implements ApplicationTool, ApplicationContextDatabaseMigrationCommand {

    final String description = 'Generates an initial changelog XML or Groovy DSL file from the database'

    void handle() {
        def filename = args[0]

        def outputChangeLogFile = resolveChangeLogFile(filename)
        if (outputChangeLogFile) {
            if (outputChangeLogFile.exists()) {
                if (hasOption('force')) {
                    outputChangeLogFile.delete()
                } else {
                    throw new DatabaseMigrationException("ChangeLogFile ${outputChangeLogFile} already exists!")
                }
            }
            if (!outputChangeLogFile.parentFile.exists()) {
                outputChangeLogFile.parentFile.mkdirs()
            }
        }

        withDatabase { Database database ->
            doGenerateChangeLog(outputChangeLogFile, database)
        }

        if (outputChangeLogFile && hasOption('add')) {
            appendToChangeLog(changeLogFile, outputChangeLogFile)
        }
    }
}
