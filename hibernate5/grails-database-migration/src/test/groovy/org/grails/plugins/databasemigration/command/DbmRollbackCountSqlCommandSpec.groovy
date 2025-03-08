/*
 * Copyright 2015-2024 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.plugins.databasemigration.command

import grails.dev.commands.ApplicationCommand
import org.grails.plugins.databasemigration.DatabaseMigrationException
import spock.lang.AutoCleanup

class DbmRollbackCountSqlCommandSpec extends ApplicationContextDatabaseMigrationCommandSpec {

    @Override
    protected Class<ApplicationCommand> getCommandClass() {
        return DbmRollbackCountSqlCommand
    }

    @AutoCleanup('delete')
    File outputFile = File.createTempFile('rollback', 'sql')

    def setup() {
        command.changeLogFile << CHANGE_LOG_CONTENT

        new DbmUpdateCommand(applicationContext: applicationContext).handle(getExecutionContext())

        def tables = sql.rows('SELECT table_name FROM information_schema.tables WHERE table_class = \'org.h2.mvstore.db.MVTable\'').collect { it.table_name.toLowerCase() }
        assert tables as Set == ['book', 'author', 'databasechangeloglock', 'databasechangelog'] as Set
    }

    def "writes the SQL to roll back the specified number of change sets to STDOUT"() {
        when:
            command.handle(getExecutionContext('1'))

        then:
            def output = output.toString()
            output.contains('DROP TABLE PUBLIC.book;')
            !output.contains('DROP TABLE PUBLIC.author;')
    }

    def "writes the SQL to roll back the specified number of change sets to a file given as arguments"() {
        when:
            command.handle(getExecutionContext('1', outputFile.canonicalPath))

        then:
            def output = outputFile.text
            output.contains('DROP TABLE PUBLIC.book;')
            !output.contains('DROP TABLE PUBLIC.author;')

    }

    def "an error occurs if the count parameter is not specified"() {
        when:
            command.handle(getExecutionContext())

        then:
            def e = thrown(DatabaseMigrationException)
            e.message == "The ${command.name} command requires a change set number argument"
    }

    def "an error occurs if the count parameter is not number"() {
        when:
            command.handle(getExecutionContext('one'))

        then:
            def e = thrown(DatabaseMigrationException)
            e.message == 'The change set number argument \'one\' isn\'t a number'
    }

    static final String CHANGE_LOG_CONTENT = '''
databaseChangeLog = {

    changeSet(author: "John Smith", id: "1") {
        createTable(tableName: "author") {
            column(autoIncrement: "true", name: "id", type: "BIGINT") {
                constraints(primaryKey: "true", primaryKeyName: "authorPK")
            }

            column(name: "version", type: "BIGINT") {
                constraints(nullable: "false")
            }

            column(name: "name", type: "VARCHAR(255)") {
                constraints(nullable: "false")
            }
        }
    }

    changeSet(author: "John Smith", id: "2") {
        createTable(tableName: "book") {
            column(autoIncrement: "true", name: "id", type: "BIGINT") {
                constraints(primaryKey: "true", primaryKeyName: "bookPK")
            }

            column(name: "version", type: "BIGINT") {
                constraints(nullable: "false")
            }

            column(name: "author_id", type: "BIGINT") {
                constraints(nullable: "false")
            }

            column(name: "title", type: "VARCHAR(255)") {
                constraints(nullable: "false")
            }
        }
    }
}
'''
}
