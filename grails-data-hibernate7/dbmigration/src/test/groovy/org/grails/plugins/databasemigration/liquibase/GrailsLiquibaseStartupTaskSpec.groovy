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
import liquibase.exception.UnexpectedLiquibaseException
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

    /** How Liquibase is given a listener class from outside, on the command line or in a properties file. */
    private static final String CONFIGURED_LISTENER_PROPERTY = 'liquibase.command.changeExecListenerClass'

    BufferingApplicationStartup recorder = new BufferingApplicationStartup(1000)

    GenericApplicationContext context

    void setup() {
        context = new GenericApplicationContext()
        context.applicationStartup = recorder
        context.refresh()
    }

    void cleanup() {
        context.close()
        System.clearProperty(CONFIGURED_LISTENER_PROPERTY)
        ConfiguredListener.ran.clear()
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
        given: 'a context whose start nothing records, with migration callbacks that see what listens to the update'
        AuditingCallbacks callbacks = new AuditingCallbacks()
        GenericApplicationContext unrecorded = contextWith(callbacks, null)
        DataSource dataSource = newDatabase()

        when:
        update(dataSource, 'dataSource', unrecorded)

        then: 'no task is reported anywhere, and nothing was set to listen to the update on behalf of one'
        taskStep() == null
        callbacks.listenersAlreadySet == 0

        and: 'the database was updated'
        tables(dataSource).containsAll(['BOOK', 'AUTHOR'])

        cleanup:
        unrecorded.close()
    }

    void 'a change listener a migration callback sets of its own hears of every change set, as does the one that reports each'() {
        given: 'migration callbacks that listen to the change sets themselves'
        AuditingCallbacks callbacks = new AuditingCallbacks()
        GenericApplicationContext withCallbacks = contextWith(callbacks, recorder)

        when:
        update(newDatabase(), 'dataSource', withCallbacks)

        then: 'the callbacks hear of every change set, as they did before the update was reported'
        callbacks.ran == ['create-book', 'create-author']

        and: 'the listener that reports the update was set after the callbacks ran, and heard of every change set too'
        callbacks.listenersAlreadySet == 0
        StartupStep task = taskStep()
        tags(task)[StartupTask.TOTAL_TAG] == '2'
        items(task) == ['create-book', 'create-author']

        cleanup:
        withCallbacks.close()
    }

    void 'a change listener class configured for Liquibase hears of every change set, whoever else listens'() {
        given: 'a listener class configured for Liquibase, and migration callbacks listening themselves'
        System.setProperty(CONFIGURED_LISTENER_PROPERTY, ConfiguredListener.name)
        AuditingCallbacks callbacks = new AuditingCallbacks()
        GenericApplicationContext withCallbacks = contextWith(callbacks, recorder)

        when:
        update(newDatabase(), 'dataSource', withCallbacks)

        then: 'the configured listener, the callbacks and the task all hear of every change set'
        ConfiguredListener.ran == ['create-book', 'create-author']
        callbacks.ran == ['create-book', 'create-author']
        items(taskStep()) == ['create-book', 'create-author']

        and: 'the configured listener was set before the callbacks ran, as it would be with no update reported'
        callbacks.listenersAlreadySet == 1

        cleanup:
        withCallbacks.close()
    }

    void 'a change log parameter a migration callback sets is used by the update, whether its start is recorded or not (recorded: #recorded)'() {
        given: 'migration callbacks that name a table with a change log parameter as the update starts'
        ParameterCallbacks callbacks = new ParameterCallbacks(shelfTable: 'shelf_from_callback')
        GenericApplicationContext withCallbacks = contextWith(callbacks, recorded ? recorder : null)
        DataSource dataSource = newDatabase()

        when:
        update(dataSource, 'dataSource', withCallbacks, 'startup-task-parameter-changelog.xml')

        then: 'the change set created the table the callback named'
        tables(dataSource).contains('SHELF_FROM_CALLBACK')

        and: 'the change set was reported when the start is recorded, and nothing was reported when it is not'
        recorded ? items(taskStep()) == ['create-named-shelf'] : taskStep() == null

        cleanup:
        withCallbacks.close()

        where:
        recorded << [true, false]
    }

    void 'an update that fails on a change set ends the task as it fails, at the change set that failed'() {
        given:
        DataSource dataSource = newDatabase()

        when: 'the database is updated with a change log whose second change set fails'
        update(dataSource, 'dataSource', context, 'startup-task-failing-changelog.xml')

        then: 'the update fails'
        thrown(UnexpectedLiquibaseException)

        and: 'the task was ended all the same, with the change sets up to and including the one that failed as its items'
        StartupStep task = taskStep()
        tags(task)[StartupTask.TOTAL_TAG] == '3'
        items(task) == ['create-book', 'index-the-shelf']

        and: 'the database was updated up to the change set that failed'
        tables(dataSource).contains('BOOK')
        !tables(dataSource).contains('AUTHOR')
    }

    private void update(DataSource dataSource, String dataSourceName, GenericApplicationContext applicationContext = context,
                        String changeLog = 'startup-task-changelog.xml') {
        GrailsLiquibase liquibase = new GrailsLiquibase(applicationContext)
        liquibase.dataSource = dataSource
        liquibase.changeLog = changeLog
        liquibase.contexts = 'production'
        liquibase.dataSourceName = dataSourceName
        liquibase.afterPropertiesSet()
    }

    /**
     * A context with the given migration callbacks, whose start the given recorder records, or nothing does
     * when it is {@code null}.
     */
    private static GenericApplicationContext contextWith(Object callbacks, BufferingApplicationStartup recorder) {
        GenericApplicationContext applicationContext = new GenericApplicationContext()
        if (recorder) {
            applicationContext.applicationStartup = recorder
        }
        applicationContext.beanFactory.registerSingleton('migrationCallbacks', callbacks)
        applicationContext.refresh()
        applicationContext
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

        /** How many listeners the update had before the callbacks set their own. */
        int listenersAlreadySet

        void onStartMigration(Database database, Liquibase liquibase, String changeLog) {
            listenersAlreadySet = liquibase.defaultChangeExecListener.listeners.size()
            liquibase.changeExecListener = new AbstractChangeExecListener() {
                @Override
                void ran(ChangeSet changeSet, DatabaseChangeLog databaseChangeLog, Database changed, ChangeSet.ExecType execType) {
                    ran << changeSet.id
                }
            }
        }
    }

    /** Migration callbacks, duck-typed as the plugin calls them, that set a change log parameter as the update starts. */
    static class ParameterCallbacks {

        String shelfTable

        void onStartMigration(Database database, Liquibase liquibase, String changeLog) {
            liquibase.setChangeLogParameter('shelfTable', shelfTable)
        }
    }

    /** A change listener as one configured for Liquibase by class name, which Liquibase creates itself. */
    static class ConfiguredListener extends AbstractChangeExecListener {

        static List<String> ran = []

        @Override
        void ran(ChangeSet changeSet, DatabaseChangeLog databaseChangeLog, Database database, ChangeSet.ExecType execType) {
            ran << changeSet.id
        }
    }
}
