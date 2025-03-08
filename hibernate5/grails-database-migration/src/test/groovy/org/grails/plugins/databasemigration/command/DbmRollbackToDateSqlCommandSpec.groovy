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

class DbmRollbackToDateSqlCommandSpec extends ApplicationContextDatabaseMigrationCommandSpec {

    @Override
    protected Class<ApplicationCommand> getCommandClass() {
        return DbmRollbackToDateSqlCommand
    }

    @AutoCleanup('delete')
    File outputFile = File.createTempFile('rollback', 'sql')

    def setup() {
        command.changeLogFile << CHANGE_LOG_CONTENT

        new DbmUpdateCommand(applicationContext: applicationContext).handle(getExecutionContext())
        sql.executeUpdate('UPDATE PUBLIC.DATABASECHANGELOG SET DATEEXECUTED = \'2015-01-02 12:00:00\' WHERE ID = \'1\'')

        def tables = sql.rows('SELECT table_name FROM information_schema.tables WHERE table_class = \'org.h2.mvstore.db.MVTable\'').collect { it.table_name.toLowerCase() }
        assert tables as Set == ['book', 'author', 'databasechangeloglock', 'databasechangelog'] as Set
    }

    def "writes SQL to roll back the database to the state it was in when the tag was applied to STDOUT"() {
        when:
            command.handle(getExecutionContext(args as String[]))

        then:
            def output = output.toString()
            output.contains('DROP TABLE PUBLIC.book;')
            !output.contains('DROP TABLE PUBLIC.author;')

        where:
            args << [['2015-01-03'], ['2015-01-02', '13:00:00']]
    }

    def "writes SQL to roll back the database to the state it was in when the tag was applied to a file given as arguments"() {
        when:
            command.handle(getExecutionContext(((args << outputFile.canonicalPath) as String[])))

        then:
            def output = outputFile.text
            output.contains('DROP TABLE PUBLIC.book;')
            !output.contains('DROP TABLE PUBLIC.author;')

        where:
            args << [['2015-01-03'], ['2015-01-02', '13:00:00']]
    }

    def "an error occurs if the date parameter is not specified"() {
        when:
            command.handle(getExecutionContext())

        then:
            def e = thrown(DatabaseMigrationException)
            e.message == 'Date must be specified as two strings with the format "yyyy-MM-dd HH:mm:ss" or as one strings with the format "yyyy-MM-dd"'
    }

    def "an error occurs if the date parameter is invalid format"() {
        when:
            command.handle(getExecutionContext(args as String[]))

        then:
            def e = thrown(DatabaseMigrationException)
            e.message.startsWith("Problem parsing '${args.join(' ')}' as a Date")

        where:
            args << [['XXXX-01-03'], ['XXXX-01-02', '13:00:00']]
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
