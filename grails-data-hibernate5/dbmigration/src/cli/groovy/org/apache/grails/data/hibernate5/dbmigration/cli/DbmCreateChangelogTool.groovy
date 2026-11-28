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

import groovy.transform.CompileStatic

import liquibase.serializer.ChangeLogSerializer
import liquibase.serializer.ChangeLogSerializerFactory

import org.apache.grails.core.cli.ApplicationTool
import org.apache.grails.core.cli.ToolHints
import org.apache.grails.core.cli.ToolOption
import org.apache.grails.core.cli.ToolParameter
import org.grails.plugins.databasemigration.DatabaseMigrationException

@ToolHints(readOnlyHint = false, destructiveHint = true)
@DatabaseMigrationOptions
@ToolOption(names = ['--force'], arity = '0', description = 'Replaces the change log when it exists')
@ToolOption(names = ['--add'], arity = '0', description = 'Includes the change log it writes in the root change log')
@ToolParameter(index = 0, label = 'FILE', arity = '1', description = 'The change log to create, in the folder of the change logs')
@CompileStatic
class DbmCreateChangelogTool implements ApplicationTool, ApplicationContextDatabaseMigrationCommand {

    final String description = 'Creates an empty changelog file'

    @Override
    void handle() {
        def filename = args[0]
        if (!filename) {
            throw new DatabaseMigrationException("The $name command requires a filename")
        }

        def outputChangeLogFile = resolveChangeLogFile(filename)
        // the format is the one of the file that is written: a name without an extension is a Groovy change log.
        // It is found before a file that is there is replaced, so that a format Liquibase does not write leaves it
        ChangeLogSerializer serializer = ChangeLogSerializerFactory.instance.getSerializer(outputChangeLogFile.name)
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

        outputChangeLogFile.withOutputStream { OutputStream out ->
            serializer.write([], out)
        }

        if (hasOption('add')) {
            appendToChangeLog(changeLogFile, outputChangeLogFile)
        }
    }
}
