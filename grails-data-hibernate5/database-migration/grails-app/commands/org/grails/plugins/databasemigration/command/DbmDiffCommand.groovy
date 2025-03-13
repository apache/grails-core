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
package org.grails.plugins.databasemigration.command

import grails.dev.commands.ApplicationCommand
import grails.util.Environment
import groovy.transform.CompileStatic
import liquibase.database.Database
import org.grails.plugins.databasemigration.DatabaseMigrationException

@CompileStatic
class DbmDiffCommand implements ApplicationCommand, ApplicationContextDatabaseMigrationCommand {

    final String description = 'Compares two databases and creates a changelog that will make the changes required to bring them into sync'

    void handle() {
        def otherEnv = args[0]
        if (!otherEnv) {
            throw new DatabaseMigrationException('You must specify the environment to diff against')
        }
        if (Environment.getEnvironment(otherEnv) == Environment.current || otherEnv == Environment.current.name) {
            throw new DatabaseMigrationException('You must specify a different environment than the one the command is running in')
        }

        def filename = args[1]

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

        withDatabase { Database referenceDatabase ->
            withDatabase(getDataSourceConfig(getEnvironmentConfig(otherEnv))) { Database targetDatabase ->
                doDiffToChangeLog(outputChangeLogFile, referenceDatabase, targetDatabase)
            }
        }

        if (outputChangeLogFile && hasOption('add')) {
            appendToChangeLog(changeLogFile, outputChangeLogFile)
        }
    }
}
