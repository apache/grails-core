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

@CompileStatic
class DbmTagCommand implements ApplicationCommand, ApplicationContextDatabaseMigrationCommand {

    final String description = 'Adds a tag to mark the current database state'

    void handle() {
        def tagName = args[0]
        if (!tagName) {
            throw new DatabaseMigrationException("The $name command requires a tag")
        }

        withLiquibase { Liquibase liquibase ->
            liquibase.tag(tagName)
        }
    }
}
