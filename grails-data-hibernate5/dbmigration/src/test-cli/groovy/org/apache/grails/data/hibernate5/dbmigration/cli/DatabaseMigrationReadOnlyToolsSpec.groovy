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
package org.apache.grails.data.hibernate5.dbmigration.cli

import groovy.json.JsonSlurper

import spock.lang.Unroll

import org.apache.grails.core.cli.ApplicationTool

/**
 * A tool of the plugin that says it is read-only changes nothing in the database, also not the tables Liquibase
 * keeps of what has run and of its lock; those that read the database and write a report or SQL but change it are
 * not read-only, for what they change.
 */
class DatabaseMigrationReadOnlyToolsSpec extends ApplicationContextDatabaseMigrationCommandSpec {

    static final String FRESH = 'without the tables of Liquibase'

    static final String PREPARED = 'with them, after a change set has run and was tagged'

    // the tools that read the database and write a report or SQL, with their parameters, and dbm-validate
    static final Map<Class<? extends ApplicationTool>, List<String>> TOOLS = [
            (DbmChangelogSyncSqlTool)       : [],
            (DbmFutureRollbackCountSqlTool) : ['1'],
            (DbmFutureRollbackSqlTool)      : [],
            (DbmListLocksTool)              : [],
            (DbmMarkNextChangesetRanSqlTool): [],
            (DbmPreviousChangesetSqlTool)   : ['1'],
            (DbmRollbackCountSqlTool)       : ['1'],
            (DbmRollbackSqlTool)            : ['a-tag'],
            (DbmRollbackToDateSqlTool)      : ['2000-01-01'],
            (DbmStatusTool)                 : [],
            (DbmUpdateCountSqlTool)         : ['1'],
            (DbmUpdateSqlTool)              : [],
            (DbmValidateTool)               : [],
    ]

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

    @Unroll
    def "#name, which is read-only, changes nothing in a database #state"() {
        given:
            prepare(state)
            Map before = databaseState()

        when:
            run(tool)

        then:
            databaseState() == before

        where:
            [tool, state] << [readOnlyTools(), [FRESH, PREPARED]].combinations()
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
            tool                        | state    | what                                                                      | changed
            DbmStatusTool               | FRESH    | 'it makes the tables of Liquibase in a database without them'           | { Map b, Map a -> b.tables == [] && a.tables.containsAll(LIQUIBASE_TABLES) }
            DbmListLocksTool            | FRESH    | 'it makes the tables of Liquibase in a database without them'           | { Map b, Map a -> b.tables == [] && a.tables.containsAll(LIQUIBASE_TABLES) }
            DbmValidateTool             | FRESH    | 'it makes the tables of Liquibase in a database without them'           | { Map b, Map a -> b.tables == [] && a.tables.containsAll(LIQUIBASE_TABLES) }
            DbmPreviousChangesetSqlTool | FRESH    | 'it makes the tables of Liquibase in a database without them'           | { Map b, Map a -> b.tables == [] && a.tables.containsAll(LIQUIBASE_TABLES) }
            DbmUpdateSqlTool            | PREPARED | 'it writes the lock of Liquibase in the database as it releases the lock' | { Map b, Map a -> b.lock != a.lock }
            DbmUpdateCountSqlTool       | PREPARED | 'it writes the lock of Liquibase in the database as it releases the lock' | { Map b, Map a -> b.lock != a.lock }
            name = descriptorOf(tool).name
    }

    static final List<String> LIQUIBASE_TABLES = ['DATABASECHANGELOG', 'DATABASECHANGELOGLOCK']

    // a database with what the state says; the lock row of a prepared one has values that taking and releasing the lock clears
    private void prepare(String state) {
        if (state == PREPARED) {
            new DbmUpdateCountTool(applicationContext: applicationContext).handle(getExecutionContext(DbmUpdateCountTool, '1'))
            new DbmTagTool(applicationContext: applicationContext).handle(getExecutionContext(DbmTagTool, 'a-tag'))
            sql.executeUpdate("UPDATE DATABASECHANGELOGLOCK SET LOCKGRANTED = TIMESTAMP '2001-01-01 00:00:00', LOCKEDBY = 'someone'")
        }
    }

    // runs a tool to the standard output; what it changes counts, also when it fails
    private void run(Class<? extends ApplicationTool> tool) {
        try {
            createCommand((Class<ApplicationTool>) tool).handle(getExecutionContext((Class<ApplicationTool>) tool, *TOOLS[tool]))
        } catch (Exception ignored) {
            // a tool that fails, as dbm-rollback-sql for a tag that is not there, is still held to what it changes
        }
    }

    private Map databaseState() {
        List<String> tables = sql.rows("SELECT table_name FROM information_schema.tables WHERE table_schema = 'PUBLIC'")
                .collect { it.table_name.toString() }.sort()
        Map state = [tables: tables]
        if (tables.contains('DATABASECHANGELOG')) {
            state.changeLog = sql.rows('SELECT * FROM DATABASECHANGELOG ORDER BY ORDEREXECUTED').collect { it.toString() }
        }
        if (tables.contains('DATABASECHANGELOGLOCK')) {
            state.lock = sql.rows('SELECT * FROM DATABASECHANGELOGLOCK').collect { it.toString() }
        }
        state
    }

    private static List<Class<? extends ApplicationTool>> readOnlyTools() {
        TOOLS.keySet().findAll { Class<? extends ApplicationTool> tool -> descriptorOf(tool).hints.readOnlyHint }.toList()
    }

    private static Map descriptorOf(Class<? extends ApplicationTool> tool) {
        URL descriptor = DatabaseMigrationReadOnlyToolsSpec.classLoader.getResource("META-INF/grails-tools/${tool.name}.json")
        assert descriptor != null
        (Map) new JsonSlurper().parse(descriptor, 'UTF-8')
    }
}
