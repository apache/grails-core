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
import org.grails.plugins.databasemigration.DatabaseMigrationException

class DbmGenerateGormChangelogCommandSpec extends ApplicationContextDatabaseMigrationCommandSpec {

    @Override
    protected Class<ApplicationCommand> getCommandClass() {
        return DbmGenerateGormChangelogCommand
    }

    def "writes Change Log to copy the current state of the database to STDOUT"() {
        when:
            command.handle(getExecutionContext())

        then:
        extractOutput(output) =~ '''
databaseChangeLog = \\{

    changeSet\\(author: ".+?", id: ".+?"\\) \\{
        createTable\\(tableName: "author"\\) \\{
            column\\(autoIncrement: "true", name: "id", type: "BIGINT"\\) \\{
                constraints\\(nullable: "false", primaryKey: "true", primaryKeyName: "authorPK"\\)
            \\}

            column\\(name: "version", type: "BIGINT"\\) \\{
                constraints\\(nullable: "false"\\)
            \\}

            column\\(name: "name", type: "VARCHAR\\(255\\)"\\) \\{
                constraints\\(nullable: "false"\\)
            \\}
        \\}
    \\}

    changeSet\\(author: ".+?", id: ".+?"\\) \\{
        createTable\\(tableName: "book"\\) \\{
            column\\(autoIncrement: "true", name: "id", type: "BIGINT"\\) \\{
                constraints\\(nullable: "false", primaryKey: "true", primaryKeyName: "bookPK"\\)
            \\}

            column\\(name: "version", type: "BIGINT"\\) \\{
                constraints\\(nullable: "false"\\)
            \\}

            column\\(name: "title", type: "VARCHAR\\(255\\)"\\) \\{
                constraints\\(nullable: "false"\\)
            \\}

            column\\(name: "author_id", type: "BIGINT"\\) \\{
                constraints\\(nullable: "false"\\)
            \\}
        \\}
    \\}

    changeSet\\(author: ".+?", id: ".+?"\\) \\{
        addForeignKeyConstraint\\(baseColumnNames: "author_id", baseTableName: "book", constraintName: "FK.+?", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "author", validate: "true"\\)
    \\}
\\}
'''.replaceAll(/\s/,"")
    }

    def "writes Change Log to copy the current state of the database to a file given as arguments"() {
        given:
            def filename = 'changelog.groovy'

        when:
            command.handle(getExecutionContext(filename))

        then:
            def output = new File(changeLogLocation, filename).text?.replaceAll(/\s/, "")
            output =~ '''
databaseChangeLog = \\{

    changeSet\\(author: ".+?", id: ".+?"\\) \\{
        createTable\\(tableName: "author"\\) \\{
            column\\(autoIncrement: "true", name: "id", type: "BIGINT"\\) \\{
                constraints\\(nullable: "false", primaryKey: "true", primaryKeyName: "authorPK"\\)
            \\}

            column\\(name: "version", type: "BIGINT"\\) \\{
                constraints\\(nullable: "false"\\)
            \\}

            column\\(name: "name", type: "VARCHAR\\(255\\)"\\) \\{
                constraints\\(nullable: "false"\\)
            \\}
        \\}
    \\}

    changeSet\\(author: ".+?", id: ".+?"\\) \\{
        createTable\\(tableName: "book"\\) \\{
            column\\(autoIncrement: "true", name: "id", type: "BIGINT"\\) \\{
                constraints\\(nullable: "false", primaryKey: "true", primaryKeyName: "bookPK"\\)
            \\}

            column\\(name: "version", type: "BIGINT"\\) \\{
                constraints\\(nullable: "false"\\)
            \\}

            column\\(name: "title", type: "VARCHAR\\(255\\)"\\) \\{
                constraints\\(nullable: "false"\\)
            \\}
            
            column\\(name: "author_id", type: "BIGINT"\\) \\{
                constraints\\(nullable: "false"\\)
            \\}
        \\}
    \\}

    changeSet\\(author: ".+?", id: ".+?"\\) \\{
        addForeignKeyConstraint\\(baseColumnNames: "author_id", baseTableName: "book", constraintName: "FK.+?", deferrable: "false", initiallyDeferred: "false", referencedColumnNames: "id", referencedTableName: "author", validate: "true"\\)
    \\}
\\}
'''.replaceAll(/\s/, "")
    }

    def "an error occurs if changeLogFile already exists"() {
        given:
            def filename = 'changelog.yml'
            def changeLogFile = new File(changeLogLocation, filename)
            assert changeLogFile.createNewFile()

        when:
            command.handle(getExecutionContext(filename))

        then:
            def e = thrown(DatabaseMigrationException)
            e.message == "ChangeLogFile ${changeLogFile.canonicalPath} already exists!"
    }

    @Override
    protected Class[] getDomainClasses() {
        [Book, Author] as Class[]
    }
}
