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

import groovy.transform.CompileStatic
import liquibase.serializer.ChangeLogSerializer
import liquibase.serializer.ChangeLogSerializerFactory
import org.grails.plugins.databasemigration.DatabaseMigrationException

@CompileStatic
class DbmCreateChangelog implements ScriptDatabaseMigrationCommand {

    @Override
    void handle() {
        def filename = args[0]
        if (!filename) {
            throw new DatabaseMigrationException("The $name command requires a filename")
        }

        def outputChangeLogFile = resolveChangeLogFile(filename)
        if (outputChangeLogFile.exists()) {
            if (hasOption('force')) {
                outputChangeLogFile.delete()
            } else {
                throw new DatabaseMigrationException("ChangeLogFile ${outputChangeLogFile} already exists!")
            }
        }
        if (!outputChangeLogFile.parentFile.exists()) {
            outputChangeLogFile.parentFile.mkdirs()
        }

        ChangeLogSerializer serializer = ChangeLogSerializerFactory.instance.getSerializer(filename)

        outputChangeLogFile.withOutputStream { OutputStream out ->
            serializer.write([], out)
        }

        if (hasOption('add')) {
            appendToChangeLog(changeLogFile, outputChangeLogFile)
        }
    }
}
