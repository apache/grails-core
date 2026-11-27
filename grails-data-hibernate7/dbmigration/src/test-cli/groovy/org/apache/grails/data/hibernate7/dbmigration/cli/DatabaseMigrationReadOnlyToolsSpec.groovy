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

import groovy.json.JsonSlurper

import spock.lang.Unroll

import org.apache.grails.core.cli.ApplicationTool

/**
 * What the tools of the plugin that read the database and write a report or SQL do to it, which their hints say.
 * The tools whose SQL Liquibase only prints change nothing at all. dbm-status, dbm-list-locks and dbm-validate never
 * change the schema or the data of the application and never run or record a change set; in a database Liquibase has
 * not been used with they create its two bookkeeping tables, as any first use of the plugin does. Both kinds are
 * read-only. The others change the database, and are not.
 */
class DatabaseMigrationReadOnlyToolsSpec extends ApplicationContextDatabaseMigrationCommandSpec {

    static final String FRESH = 'Liquibase has not been used with'

    static final String PREPARED = 'Liquibase has been used with, where a change set has run and was tagged'

    static final String MISSING_CHECKSUM = 'Liquibase has been used with, where the checksum of the change set that has run is missing'

    // the tools whose SQL Liquibase only prints, with their parameters
    static final Map<Class<? extends ApplicationTool>, List<String>> SQL_ONLY = [
            (DbmChangelogSyncSqlTool)       : [],
            (DbmFutureRollbackCountSqlTool) : ['1'],
            (DbmFutureRollbackSqlTool)      : [],
            (DbmMarkNextChangesetRanSqlTool): [],
            (DbmRollbackCountSqlTool)       : ['1'],
            (DbmRollbackSqlTool)            : ['a-tag'],
            (DbmRollbackToDateSqlTool)      : ['2000-01-01'],
    ]

    // the tools that read what Liquibase keeps, which create its bookkeeping tables where they are not there
    static final Map<Class<? extends ApplicationTool>, List<String>> FIRST_USE = [
            (DbmListLocksTool): [],
            (DbmStatusTool)   : [],
            (DbmValidateTool) : [],
    ]

    // the other tools that read the database and write a report or SQL
    static final Map<Class<? extends ApplicationTool>, List<String>> OTHERS = [
            (DbmPreviousChangesetSqlTool): ['1'],
            (DbmUpdateCountSqlTool)      : ['1'],
            (DbmUpdateSqlTool)           : [],
    ]

    static final List<String> BOOKKEEPING_TABLES = ['DATABASECHANGELOG', 'DATABASECHANGELOGLOCK']

    static final String CHANGE_LOG = '''
databaseChangeLog = {
    changeSet(author: "John Smith", id: "1") {
        createTable(tableName: "author") {
            column(name: "id", type: "BIGINT")
        }
    }
    changeSet(author: "John Smith", id: "2") {
        createTable(tableName: "book") {
            column(name: "id", type: "BIGINT")
        }
    }
}
'''

    def setup() {
        createCommand(DbmUpdateTool).changeLogFile.text = CHANGE_LOG
    }

    def "the read-only tools among them are those whose SQL Liquibase only prints, and the three that read what Liquibase keeps"() {
        when:
            Map<Class<? extends ApplicationTool>, List<String>> all = SQL_ONLY + FIRST_USE + OTHERS

        then:
            all.keySet().findAll { Class<? extends ApplicationTool> tool -> descriptorOf(tool).hints.readOnlyHint } as Set ==
                    (SQL_ONLY.keySet() + FIRST_USE.keySet()) as Set
    }

    @Unroll
    def "#name changes nothing in a database #state"() {
        given:
            prepare(state)
            Map before = databaseState()

        when:
            run(tool)

        then:
            databaseState() == before

        where:
            [tool, state] << [SQL_ONLY.keySet().toList(), [FRESH, PREPARED, MISSING_CHECKSUM]].combinations() +
                    [FIRST_USE.keySet().toList(), [PREPARED, MISSING_CHECKSUM]].combinations()
            name = descriptorOf(tool).name
    }

    @Unroll
    def "#name, in a database Liquibase has not been used with, creates the two bookkeeping tables of Liquibase and nothing else"() {
        given:
            prepare(FRESH)
            Map before = databaseState()

        when:
            run(tool)
            Map after = databaseState()

        then: 'the database had no table, and has the two of Liquibase: no change set recorded, and the lock Liquibase makes free'
            before.tables == []
            after.tables == BOOKKEEPING_TABLES
            after.changeLog == []
            after.lock == [[ID: 1, LOCKED: false, LOCKGRANTED: null, LOCKEDBY: null]]

        where:
            tool << FIRST_USE.keySet().toList()
            name = descriptorOf(tool).name
    }

    @Unroll
    def "#name is not read-only, since #what"() {
        given:
            prepare(state)
            Map before = databaseState()

        when:
            run(tool)
            Map after = databaseState()

        then:
            !descriptorOf(tool).hints.readOnlyHint
            changed(before, after)

        where:
            tool                        | state            | what                                                                         | changed
            DbmPreviousChangesetSqlTool | MISSING_CHECKSUM | 'it fills in the checksum of a change set that has run where it is missing' | { Map b, Map a -> b.changeLog*.MD5SUM == [null] && a.changeLog*.MD5SUM.every() }
            DbmUpdateSqlTool            | PREPARED         | 'it writes the lock of Liquibase in the database as it releases the lock'    | { Map b, Map a -> b.lock != a.lock }
            DbmUpdateCountSqlTool       | PREPARED         | 'it writes the lock of Liquibase in the database as it releases the lock'    | { Map b, Map a -> b.lock != a.lock }
            name = descriptorOf(tool).name
    }

    // a database with what the state says; the lock row of one Liquibase has been used with has values that taking and
    // releasing the lock clears
    private void prepare(String state) {
        if (state != FRESH) {
            new DbmUpdateCountTool(applicationContext: applicationContext).handle(getExecutionContext(DbmUpdateCountTool, '1'))
            new DbmTagTool(applicationContext: applicationContext).handle(getExecutionContext(DbmTagTool, 'a-tag'))
            sql.executeUpdate("UPDATE DATABASECHANGELOGLOCK SET LOCKGRANTED = TIMESTAMP '2001-01-01 00:00:00', LOCKEDBY = 'someone'")
        }
        if (state == MISSING_CHECKSUM) {
            sql.executeUpdate('UPDATE DATABASECHANGELOG SET MD5SUM = NULL')
        }
    }

    // runs a tool to the standard output; what it changes counts, also when it fails
    private void run(Class<? extends ApplicationTool> tool) {
        List<String> arguments = (SQL_ONLY + FIRST_USE + OTHERS)[tool]
        try {
            createCommand((Class<ApplicationTool>) tool).handle(getExecutionContext((Class<ApplicationTool>) tool, *arguments))
        } catch (Exception ignored) {
            // a tool that fails, as dbm-rollback-sql for a tag that is not there, is still held to what it changes
        }
    }

    private Map databaseState() {
        List<String> tables = sql.rows("SELECT table_name FROM information_schema.tables WHERE table_schema = 'PUBLIC'")
                .collect { it.table_name.toString() }.sort()
        Map state = [tables: tables]
        if (tables.contains('DATABASECHANGELOG')) {
            state.changeLog = sql.rows('SELECT * FROM DATABASECHANGELOG ORDER BY ORDEREXECUTED').collect { new LinkedHashMap(it) }
        }
        if (tables.contains('DATABASECHANGELOGLOCK')) {
            state.lock = sql.rows('SELECT * FROM DATABASECHANGELOGLOCK').collect { new LinkedHashMap(it) }
        }
        state
    }

    private static Map descriptorOf(Class<? extends ApplicationTool> tool) {
        URL descriptor = DatabaseMigrationReadOnlyToolsSpec.classLoader.getResource("META-INF/grails-tools/${tool.name}.json")
        assert descriptor != null
        (Map) new JsonSlurper().parse(descriptor, 'UTF-8')
    }
}
