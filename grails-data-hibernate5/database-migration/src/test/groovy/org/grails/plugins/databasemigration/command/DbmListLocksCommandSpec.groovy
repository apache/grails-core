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

class DbmListLocksCommandSpec extends ApplicationContextDatabaseMigrationCommandSpec {

    @Override
    protected Class<ApplicationCommand> getCommandClass() {
        return DbmListLocksCommand
    }

    @AutoCleanup('delete')
    File outputFile = File.createTempFile('locks', 'txt')

    def "lists locks on the database changelog when the lock does not exist"() {
        when:
            command.handle(getExecutionContext())

        then:
            output.toString().contains '- No locks'
    }

    def "lists locks on the database changelog when the lock exists"() {
        given:
            sql.executeUpdate('CREATE TABLE PUBLIC.DATABASECHANGELOGLOCK (ID INT NOT NULL, LOCKED BOOLEAN NOT NULL, LOCKGRANTED TIMESTAMP, LOCKEDBY VARCHAR(255), CONSTRAINT PK_DATABASECHANGELOGLOCK PRIMARY KEY (ID))')
            sql.executeUpdate('INSERT INTO PUBLIC.DATABASECHANGELOGLOCK (ID, LOCKED, LOCKGRANTED, LOCKEDBY) VALUES (1, TRUE, NOW(), \'John Smith\')')

        when:
            command.handle(getExecutionContext())

        then:
            output.toString() =~ '- John Smith at .+?'
    }

    def "lists locks to a file given as arguments"() {
        when:
            command.handle(getExecutionContext(outputFile.canonicalPath))

        then:
            outputFile.text.contains '- No locks'
    }
}
