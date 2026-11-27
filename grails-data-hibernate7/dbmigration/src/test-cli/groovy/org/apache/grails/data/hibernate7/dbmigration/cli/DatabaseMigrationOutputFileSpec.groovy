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

import java.nio.file.Files

import spock.lang.IgnoreIf
import spock.lang.TempDir
import spock.lang.Unroll

import grails.util.BuildSettings
import org.apache.grails.core.cli.ApplicationTool
import org.grails.plugins.databasemigration.DatabaseMigrationException

/**
 * The tools that write their output, SQL or a report, to a file they are given write a new file inside the
 * project and nothing else: a file outside the project and a file that is there already are refused before
 * the tool opens the database.
 */
class DatabaseMigrationOutputFileSpec extends ApplicationContextDatabaseMigrationCommandSpec {

    // the tools that write their output to a file they are given, with the parameters before the file
    static final Map<Class<? extends ApplicationTool>, List<String>> OUTPUT_TOOLS = [
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
    ]

    static final String CHANGE_LOG = '''
databaseChangeLog = {
    changeSet(author: "John Smith", id: "1") {
        createTable(tableName: "author") {
            column(name: "id", type: "BIGINT")
        }
    }
}
'''

    @TempDir
    File outside

    def setup() {
        createCommand(DbmUpdateTool).changeLogFile.text = CHANGE_LOG
    }

    // a database the tools cannot reach: a tool that opens it fails, so one that is refused first never opened it
    private void unreachableDatabase() {
        configure(['dataSource.url': 'jdbc:h2:tcp://localhost:1/unreachable'])
    }

    private void handle(Class<? extends ApplicationTool> tool, String file) {
        createCommand((Class<ApplicationTool>) tool).handle(getExecutionContext((Class<ApplicationTool>) tool, *(OUTPUT_TOOLS[tool] + [file])))
    }

    @Unroll
    def "#tool.simpleName opens the database it is configured with, which here it cannot reach"() {
        given:
            unreachableDatabase()
            File file = newOutputFile('output.sql')

        when:
            handle(tool, file.path)

        then:
            Exception e = thrown()
            !(e instanceof DatabaseMigrationException)
            !file.exists()

        where:
            tool << OUTPUT_TOOLS.keySet().toList()
    }

    @Unroll
    def "#tool.simpleName does not replace a file that is there, and stops before it opens the database"() {
        given:
            unreachableDatabase()
            File file = newOutputFile('there.sql')
            file.parentFile.mkdirs()
            byte[] content = 'written by hand\n'.bytes
            file.bytes = content

        when:
            handle(tool, file.path)

        then:
            DatabaseMigrationException e = thrown()
            e.message == "The output file ${file.toPath().toRealPath()} is there already: remove it or name another file".toString()
            file.bytes == content

        where:
            tool << OUTPUT_TOOLS.keySet().toList()
    }

    @Unroll
    def "#tool.simpleName writes no file outside the project, given #how, and stops before it opens the database"() {
        given:
            unreachableDatabase()
            String name = "dbm-outside-${UUID.randomUUID()}.sql"
            File target = how == 'an absolute path' ? new File(outside, name) : new File(projectDirectoryFile().parentFile, name)
            String file = how == 'an absolute path' ? target.path : "../${name}".toString()

        when:
            handle(tool, file)

        then:
            DatabaseMigrationException e = thrown()
            e.message == "The output file ${target.parentFile.toPath().toRealPath().resolve(name)} is outside the project ${projectDirectory()}: name a file inside the project".toString()
            !target.exists()

        where:
            [tool, how] << [OUTPUT_TOOLS.keySet().toList(), ['an absolute path', 'a path that leaves it with ..']].combinations()
    }

    @IgnoreIf({ os.windows })
    @Unroll
    def "#tool.simpleName writes no file outside the project through a link that leads out of it"() {
        given:
            unreachableDatabase()
            projectOutputDirectory.mkdirs()
            Files.createSymbolicLink(new File(projectOutputDirectory, 'linked').toPath(), outside.toPath())

        when:
            handle(tool, new File(projectOutputDirectory, 'linked/output.sql').path)

        then:
            DatabaseMigrationException e = thrown()
            e.message == "The output file ${outside.toPath().toRealPath().resolve('output.sql')} is outside the project ${projectDirectory()}: name a file inside the project".toString()
            outside.list().length == 0

        where:
            tool << OUTPUT_TOOLS.keySet().toList()
    }

    def "writes its output to a new file inside the project, in the directories of its path that it makes"() {
        given:
            File file = newOutputFile('reports/2026/update.sql')

        when:
            handle(DbmUpdateSqlTool, file.path)

        then:
            file.text.contains('CREATE TABLE PUBLIC.author')
    }

    def "writes its output to a new file named relative to the directory of the project"() {
        given:
            File file = newOutputFile('status.txt')
            String relative = projectDirectoryFile().toPath().relativize(file.toPath()).toString()

        when:
            handle(DbmStatusTool, relative)

        then:
            file.text.contains('1 changeset has not been applied')
    }

    private static String projectDirectory() {
        projectDirectoryFile().toPath().toRealPath().toString()
    }

    private static File projectDirectoryFile() {
        BuildSettings.BASE_DIR.absoluteFile
    }
}
