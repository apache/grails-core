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

import org.apache.grails.core.cli.ApplicationTool
import org.grails.plugins.databasemigration.DatabaseMigrationException

class DbmChangelogToGroovyToolSpec extends ApplicationContextDatabaseMigrationCommandSpec {

    static final String CHANGE_LOG = '''\
databaseChangeLog:
- changeSet:
    id: 1
    author: John Smith
    changes:
    - createTable:
        tableName: book
        columns:
        - column:
            name: id
            type: INT
'''

    @Override
    protected Class<ApplicationTool> getCommandClass() {
        return DbmChangelogToGroovyTool
    }

    def setup() {
        new File(changeLogLocation, 'changelog.yml').text = CHANGE_LOG
    }

    def "is the command dbm-changelog-to-groovy, with a description"() {
        expect:
            command.name == 'dbm-changelog-to-groovy'
            command.description == 'Converts a changelog file to a Groovy DSL file'
    }

    def "writes the changelog as Groovy to the file that is named"() {
        when:
            command.handle(getExecutionContext('changelog.yml', 'converted.groovy'))
            def converted = new File(changeLogLocation, 'converted.groovy').text.replaceAll(/\s/, '')

        then:
            converted.startsWith('databaseChangeLog={changeSet(author:"JohnSmith",id:"1"')
            converted.contains('{createTable(tableName:"book"){column(name:"id",type:"INT")')
    }

    def "writes the changelog as Groovy to the standard output when no file is named"() {
        when:
            command.handle(getExecutionContext('changelog.yml'))

        then:
            extractOutput(output).contains('createTable(tableName:"book")')
    }

    def "refuses a destination that is no Groovy file, and one that is there"() {
        when:
            command.handle(getExecutionContext('changelog.yml', 'converted.xml'))

        then:
            def e = thrown(DatabaseMigrationException)
            e.message.endsWith('must be a Groovy file')

        when:
            new File(changeLogLocation, 'there.groovy').text = 'kept'
            command.handle(getExecutionContext('changelog.yml', 'there.groovy'))

        then:
            e = thrown(DatabaseMigrationException)
            e.message.endsWith('already exists!')
            new File(changeLogLocation, 'there.groovy').text == 'kept'
    }

    def "overwrites a destination that is there when it is told to"() {
        given:
            def there = new File(changeLogLocation, 'there.groovy')
            there.text = 'to be replaced'

        when:
            command.handle(getExecutionContext('changelog.yml', 'there.groovy', '--force'))

        then:
            there.text.replaceAll(/\s/, '').startsWith('databaseChangeLog={changeSet(author:"JohnSmith",id:"1"')
            !there.text.contains('to be replaced')
    }

    def "writes into a directory that is not there yet"() {
        when:
            command.handle(getExecutionContext('changelog.yml', 'converted/2026/books.groovy'))

        then:
            new File(changeLogLocation, 'converted/2026/books.groovy').text.replaceAll(/\s/, '').contains('createTable(tableName:"book")')
    }

    def "adds the converted file to the changelog of the application when asked to, and not otherwise"() {
        given:
            command.changeLogFile.text = 'databaseChangeLog = {\n}\n'

        when:
            command.handle(getExecutionContext('changelog.yml', 'quiet.groovy'))

        then:
            new File(changeLogLocation, 'quiet.groovy').isFile()
            !command.changeLogFile.text.contains('include')

        when:
            command.handle(getExecutionContext('changelog.yml', 'added.groovy', '--add'))

        then:
            new File(changeLogLocation, 'added.groovy').isFile()
            command.changeLogFile.text.contains("include file: 'added.groovy'")
            !command.changeLogFile.text.contains('quiet.groovy')
    }

    def "has nothing to add to the changelog of the application when the changelog goes to the standard output"() {
        given:
            command.changeLogFile.text = 'databaseChangeLog = {\n}\n'

        when:
            command.handle(getExecutionContext('changelog.yml', '--add'))

        then:
            extractOutput(output).contains('createTable(tableName:"book")')
            command.changeLogFile.text == 'databaseChangeLog = {\n}\n'
    }

    def "needs the name of the changelog to convert"() {
        when:
            command.handle(getExecutionContext())

        then:
            def e = thrown(DatabaseMigrationException)
            e.message == 'The dbm-changelog-to-groovy command requires a source filename'
    }
}
