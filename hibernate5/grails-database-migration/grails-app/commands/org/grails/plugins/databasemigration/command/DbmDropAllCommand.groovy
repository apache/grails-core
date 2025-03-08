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
import liquibase.CatalogAndSchema
import liquibase.Liquibase

@CompileStatic
class DbmDropAllCommand implements ApplicationCommand, ApplicationContextDatabaseMigrationCommand {

    final String description = 'Drops all database objects owned by the user'

    void handle() {
        def schemaNames = args[0]
        def schemas = schemaNames?.split(',')?.collect { String schemaName -> new CatalogAndSchema(null, schemaName) }

        withLiquibase { Liquibase liquibase ->
            if (schemas) {
                liquibase.dropAll(schemas as CatalogAndSchema[])
            } else {
                liquibase.dropAll()
            }
        }
    }
}
