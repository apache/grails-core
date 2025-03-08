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
import groovy.transform.CompileStatic
import liquibase.database.Database
import org.grails.plugins.databasemigration.DatabaseMigrationException

@CompileStatic
class DbmGormDiffCommand implements ApplicationCommand, ApplicationContextDatabaseMigrationCommand {

    final String description = 'Diffs GORM classes against a database and generates a changelog XML or Groovy DSL file'

    @Override
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

        withGormDatabase(applicationContext, dataSource) { Database referenceDatabase ->
            withDatabase { Database targetDatabase ->
                doDiffToChangeLog(outputChangeLogFile, referenceDatabase, targetDatabase)
            }
        }

        if (outputChangeLogFile && hasOption('add')) {
            appendToChangeLog(changeLogFile, outputChangeLogFile)
        }
    }
}
