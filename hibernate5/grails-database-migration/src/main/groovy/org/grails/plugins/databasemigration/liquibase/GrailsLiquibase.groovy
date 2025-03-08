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
package org.grails.plugins.databasemigration.liquibase

import groovy.transform.CompileStatic
import liquibase.Liquibase
import liquibase.database.Database
import liquibase.exception.DatabaseException
import liquibase.exception.LiquibaseException
import liquibase.integration.spring.SpringLiquibase
import liquibase.resource.ResourceAccessor
import org.springframework.context.ApplicationContext
import org.springframework.core.io.DefaultResourceLoader

import java.sql.Connection

import static org.grails.plugins.databasemigration.PluginConstants.DATA_SOURCE_NAME_KEY

@CompileStatic
class GrailsLiquibase extends SpringLiquibase {

    private ApplicationContext applicationContext

    String dataSourceName

    String databaseChangeLogTableName

    String databaseChangeLogLockTableName

    GrailsLiquibase(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext
        this.resourceLoader = new DefaultResourceLoader()
    }

    @Override
    protected Liquibase createLiquibase(Connection connection) throws LiquibaseException {
        Liquibase liquibase = new Liquibase(getChangeLog(), createResourceOpener(), createDatabase (connection, null))
        if (parameters != null) {
            for (Map.Entry<String, String> entry : parameters.entrySet()) {
                liquibase.setChangeLogParameter(entry.getKey(), entry.getValue())
            }
        }
        liquibase.setChangeLogParameter(DATA_SOURCE_NAME_KEY, dataSourceName)
        if (isDropFirst()) {
            liquibase.dropAll()
        }

        return liquibase
    }


    @Override
    protected Database createDatabase(Connection connection, ResourceAccessor accessor) throws DatabaseException {
        Database database = super.createDatabase(connection, accessor)

        if (databaseChangeLogTableName) {
            database.databaseChangeLogTableName = databaseChangeLogTableName
        }
        if (databaseChangeLogLockTableName) {
            database.databaseChangeLogLockTableName = databaseChangeLogLockTableName
        }

        database
    }

    @Override
    protected void performUpdate(Liquibase liquibase) throws LiquibaseException {
        if (!applicationContext.containsBean('migrationCallbacks')) {
            super.performUpdate(liquibase)
            return
        }

        def database = liquibase.database
        def migrationCallbacks = applicationContext.getBean('migrationCallbacks')

        if (migrationCallbacks.metaClass.respondsTo(migrationCallbacks, 'beforeStartMigration')) {
            migrationCallbacks.invokeMethod('beforeStartMigration', [database] as Object[])
        }
        if (migrationCallbacks.metaClass.respondsTo(migrationCallbacks, 'onStartMigration')) {
            migrationCallbacks.invokeMethod('onStartMigration', [database, liquibase, changeLog] as Object[])
        }

        super.performUpdate(liquibase)

        if (migrationCallbacks.metaClass.respondsTo(migrationCallbacks, 'afterMigrations')) {
            migrationCallbacks.invokeMethod('afterMigrations', [database] as Object[])
        }
    }
}
