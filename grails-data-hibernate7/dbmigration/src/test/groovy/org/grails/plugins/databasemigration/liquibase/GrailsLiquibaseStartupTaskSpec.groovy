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
import javax.sql.DataSource

import liquibase.Liquibase
import liquibase.changelog.ChangeSet
import liquibase.changelog.DatabaseChangeLog
import liquibase.changelog.visitor.AbstractChangeExecListener
import liquibase.database.Database
import org.h2.jdbcx.JdbcDataSource
import spock.lang.Specification

import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup
import org.springframework.context.support.GenericApplicationContext
import org.springframework.core.metrics.StartupStep

import grails.boot.StartupTask

/**
 * A database update reports its progress as a {@link StartupTask} when the application's start is recorded,
 * as it is when the startup progress page is shown, and runs exactly as before when it is not.
 */
class GrailsLiquibaseStartupTaskSpec extends Specification {

    BufferingApplicationStartup recorder = new BufferingApplicationStartup(1000)

    GenericApplicationContext context

    void setup() {
        context = new GenericApplicationContext()
        context.applicationStartup = recorder
        context.refresh()
    }

    void cleanup() {
        context.close()
    }

    void 'an update reports each change set it runs as an item of a startup task'() {
        given:
        DataSource dataSource = newDatabase()

        when: 'the database is updated for the production context'
        update(dataSource, 'dataSource')

        then: 'a task says what it does and how many change sets it will run, leaving out the test-only one'
        StartupStep task = taskStep()
        tags(task) == [(StartupTask.DESCRIPTION_TAG): 'Running database migrations', (StartupTask.TOTAL_TAG): '2']

        and: 'each change set it ran is an item of the task, in the order they ran'
        items(task) == ['create-book', 'create-author']

        and: 'the database was updated'
        tables(dataSource).containsAll(['BOOK', 'AUTHOR'])
        !tables(dataSource).contains('SHELF')
    }

    void 'an update with nothing left to run reports a task with no items'() {
        given: 'a database already up to date'
        DataSource dataSource = newDatabase()
        update(dataSource, 'dataSource')
        recorder.drainBufferedTimeline()

        when:
        update(dataSource, 'dataSource')

        then:
        StartupStep task = taskStep()
        tags(task)[StartupTask.TOTAL_TAG] == '0'
        items(task).empty
    }

    void 'an update of another data source says which data source it updates'() {
        when:
        update(newDatabase(), 'dataSource_reports')

        then:
        tags(taskStep())[StartupTask.DESCRIPTION_TAG] == 'Running database migrations on dataSource_reports'
    }

    void 'an update nothing records reports nothing, and updates the database as before'() {
        given: 'a context whose start nothing records'
        GenericApplicationContext unrecorded = new GenericApplicationContext()
        unrecorded.refresh()
        DataSource dataSource = newDatabase()

        when:
        update(dataSource, 'dataSource', unrecorded)

        then: 'no task is reported anywhere'
        taskStep() == null

        and: 'the database was updated'
        tables(dataSource).containsAll(['BOOK', 'AUTHOR'])

        cleanup:
        unrecorded.close()
    }

    void 'a change listener a migration callback sets of its own takes the place of the one that reports each change set'() {
        given: 'migration callbacks that listen to the change sets themselves'
        GenericApplicationContext withCallbacks = new GenericApplicationContext()
        withCallbacks.applicationStartup = recorder
        AuditingCallbacks callbacks = new AuditingCallbacks()
        withCallbacks.beanFactory.registerSingleton('migrationCallbacks', callbacks)
        withCallbacks.refresh()

        when:
        update(newDatabase(), 'dataSource', withCallbacks)

        then: 'the callbacks hear of every change set, as they did before the update was reported'
        callbacks.ran == ['create-book', 'create-author']

        and: 'the task still says how many change sets the update ran'
        tags(taskStep())[StartupTask.TOTAL_TAG] == '2'

        cleanup:
        withCallbacks.close()
    }

    private void update(DataSource dataSource, String dataSourceName, GenericApplicationContext applicationContext = context) {
        GrailsLiquibase liquibase = new GrailsLiquibase(applicationContext)
        liquibase.dataSource = dataSource
        liquibase.changeLog = 'startup-task-changelog.xml'
        liquibase.contexts = 'production'
        liquibase.dataSourceName = dataSourceName
        liquibase.afterPropertiesSet()
    }

    private StartupStep taskStep() {
        recorder.bufferedTimeline.events*.startupStep.find { it.name == StartupTask.TASK_STEP }
    }

    private List<String> items(StartupStep task) {
        recorder.bufferedTimeline.events*.startupStep
                .findAll { it.name == StartupTask.ITEM_STEP && it.parentId == task.id }
                .collect { tags(it)[StartupTask.ITEM_TAG] }
    }

    private static Map<String, String> tags(StartupStep step) {
        step.tags.collectEntries { StartupStep.Tag tag -> [(tag.key): tag.value] }
    }

    private static DataSource newDatabase() {
        JdbcDataSource dataSource = new JdbcDataSource()
        dataSource.setURL("jdbc:h2:mem:startup-task-${UUID.randomUUID()};DB_CLOSE_DELAY=-1")
        dataSource.user = 'sa'
        dataSource
    }

    private static List<String> tables(DataSource dataSource) {
        Connection connection = dataSource.connection
        try {
            List<String> names = []
            def rows = connection.metaData.getTables(null, 'PUBLIC', '%', ['TABLE'] as String[])
            while (rows.next()) {
                names << rows.getString('TABLE_NAME')
            }
            names
        }
        finally {
            connection.close()
        }
    }

    /** Migration callbacks, duck-typed as the plugin calls them, that listen to the change sets of an update. */
    static class AuditingCallbacks {

        List<String> ran = []

        void onStartMigration(Database database, Liquibase liquibase, String changeLog) {
            liquibase.changeExecListener = new AbstractChangeExecListener() {
                @Override
                void ran(ChangeSet changeSet, DatabaseChangeLog databaseChangeLog, Database changed, ChangeSet.ExecType execType) {
                    ran << changeSet.id
                }
            }
        }
    }
}
