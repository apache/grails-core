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
import spock.lang.AutoCleanup

class DbmFutureRollbackSqlCommandSpec extends ApplicationContextDatabaseMigrationCommandSpec {

    @Override
    protected Class<ApplicationCommand> getCommandClass() {
        return DbmFutureRollbackSqlCommand
    }

    @AutoCleanup('delete')
    File outputFile = File.createTempFile('rollback', 'sql')

    def "writes SQL to roll back the database to STDOUT"() {
        given:
            command.changeLogFile << CHANGE_LOG_CONTENT

        when:
            command.handle(getExecutionContext())

        then:
            def output = output.toString()
            output.contains('ALTER TABLE PUBLIC.book DROP CONSTRAINT FK_4sac2ubmnqva85r8bk8fxdvbf')
            output.contains('DROP TABLE PUBLIC.author;')
            output.contains('DROP TABLE PUBLIC.book;')
    }

    def "writes SQL to roll back the database to a file given as arguments"() {
        given:
            command.changeLogFile << CHANGE_LOG_CONTENT

        when:
            command.handle(getExecutionContext(outputFile.canonicalPath))

        then:
            def output = outputFile.text
            output.contains('ALTER TABLE PUBLIC.book DROP CONSTRAINT FK_4sac2ubmnqva85r8bk8fxdvbf')
            output.contains('DROP TABLE PUBLIC.author;')
            output.contains('DROP TABLE PUBLIC.book;')
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

    changeSet(author: "John Smith", id: "3") {
        addForeignKeyConstraint(baseColumnNames: "author_id", baseTableName: "book", constraintName: "FK_4sac2ubmnqva85r8bk8fxdvbf", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "author")
    }
}
'''
}
