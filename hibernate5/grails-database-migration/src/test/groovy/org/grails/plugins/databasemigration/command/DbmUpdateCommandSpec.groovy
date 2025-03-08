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

class DbmUpdateCommandSpec extends ApplicationContextDatabaseMigrationCommandSpec {

    @Override
    protected Class<ApplicationCommand> getCommandClass() {
        return DbmUpdateCommand
    }

    def "updates database to current version"() {
        given:
            command.changeLogFile << CHANGE_LOG_CONTENT

        when:
            command.handle(getExecutionContext())

        then:
            def tables = sql.rows('SELECT table_name FROM information_schema.tables WHERE table_class = \'org.h2.mvstore.db.MVTable\'').collect { it.table_name.toLowerCase() }
            tables as Set == ['book', 'author', 'databasechangeloglock', 'databasechangelog'] as Set

        and:
            def authors = sql.rows('SELECT name FROM author').collect { it.name }
            authors as Set == ['Mary', 'Amelia'] as Set
    }

    def "updates database to current version with contexts"() {
        given:
            command.changeLogFile << CHANGE_LOG_CONTENT

        when:
            command.handle(getExecutionContext('--contexts=test'))

        then:
            def tables = sql.rows('SELECT table_name FROM information_schema.tables WHERE table_class = \'org.h2.mvstore.db.MVTable\'').collect { it.table_name.toLowerCase() }
            tables as Set == ['book', 'author', 'databasechangeloglock', 'databasechangelog'] as Set

        and:
            def authors = sql.rows('SELECT name FROM author').collect { it.name }
            authors as Set == ['Amelia'] as Set
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

    changeSet(author: "John Smith", id: "4", context: "development") {
        insert(tableName: "author") {
            column(name: "name", value: "Mary")
            column(name: "version", value: "0")
        }
    }

    changeSet(author: "John Smith", id: "5", context: "test") {
        insert(tableName: "author") {
            column(name: "name", value: "Amelia")
            column(name: "version", value: "0")
        }
    }
}
'''
}
