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

import groovy.transform.CompileStatic

import liquibase.Contexts
import liquibase.LabelExpression
import liquibase.Liquibase
import liquibase.database.Database
import liquibase.exception.DatabaseException
import liquibase.exception.LiquibaseException
import liquibase.integration.spring.SpringLiquibase
import liquibase.resource.ResourceAccessor

import org.springframework.context.ApplicationContext
import org.springframework.core.io.DefaultResourceLoader

import grails.boot.StartupTask

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
        // every change listener set on it is kept, so the one that reports the update and any a migration callback sets both run
        Liquibase liquibase = new MultiListenerLiquibase(getChangeLog(), createResourceOpener(), createDatabase(connection, null))
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
            performRecordedUpdate(liquibase)
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

        performRecordedUpdate(liquibase)

        if (migrationCallbacks.metaClass.respondsTo(migrationCallbacks, 'afterMigrations')) {
            migrationCallbacks.invokeMethod('afterMigrations', [database] as Object[])
        }
    }

    /**
     * Runs the update as a {@link StartupTask} when anything records the application's start. The task is begun
     * only once the migration callbacks have run: counting the change sets parses the change log, and Liquibase
     * runs the update from that parse, so a change log parameter a callback sets has to be set before it.
     */
    private void performRecordedUpdate(Liquibase liquibase) throws LiquibaseException {
        StartupTask task = startTask(liquibase)
        try {
            super.performUpdate(liquibase)
        }
        finally {
            task?.close()
        }
    }

    /**
     * Begins a {@link StartupTask} for the update when anything records the application's start, such as the
     * startup progress page, which then shows how many change sets are left and the one being run. Returns
     * {@code null} when nothing records the start, since knowing how many change sets there are takes one more
     * read of the database.
     */
    private StartupTask startTask(Liquibase liquibase) {
        if (!StartupTask.isRecorded(applicationContext)) {
            return null
        }
        StartupTask task = StartupTask.start(applicationContext, migrationDescription(), pendingChangeSets(liquibase))
        liquibase.changeExecListener = new StartupTaskChangeExecListener(task)
        task
    }

    /**
     * How many change sets the update will run, or {@code -1} when that cannot be told. Liquibase's own tables are
     * left alone, so they are only ever created by the update, which holds Liquibase's lock while it does so.
     */
    private int pendingChangeSets(Liquibase liquibase) {
        try {
            return liquibase.listUnrunChangeSets(new Contexts(contexts), new LabelExpression(labelFilter), false).size()
        }
        catch (LiquibaseException | RuntimeException ignored) {
            // the count only serves the progress page, and the update reports whatever stopped it
            return -1
        }
    }

    private String migrationDescription() {
        !dataSourceName || dataSourceName == 'dataSource' ? 'Running database migrations' : "Running database migrations on ${dataSourceName}".toString()
    }
}
