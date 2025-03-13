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
import liquibase.Liquibase
import org.grails.plugins.databasemigration.DatabaseMigrationException

import java.text.ParseException

@CompileStatic
class DbmRollbackToDateSqlCommand implements ApplicationCommand, ApplicationContextDatabaseMigrationCommand {

    final String description = 'Writes SQL to roll back the database to the state it was in at the given date/time to STDOUT or a file'

    @Override
    void handle() {
        def dateStr = args[0]
        if (!dateStr) {
            throw new DatabaseMigrationException('Date must be specified as two strings with the format "yyyy-MM-dd HH:mm:ss" or as one strings with the format "yyyy-MM-dd"')
        }

        String timeStr = null
        String filename = null
        if (args[1]) {
            if (args.size() > 2 || isTimeFormat(args[1])) {
                timeStr = args[1]
            } else {
                filename = args[1]
            }
        }

        def date = null
        try {
            date = parseDateTime(dateStr, timeStr)
        } catch (ParseException e) {
            throw new DatabaseMigrationException("Problem parsing '$dateStr${timeStr ? " $timeStr" : ''}' as a Date: $e.message")
        }

        filename = filename ?: args[2]

        withLiquibase { Liquibase liquibase ->
            withFileOrSystemOutWriter(filename) { Writer writer ->
                liquibase.rollback(date, contexts, writer)
            }
        }
    }
}
