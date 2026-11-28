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

import groovy.transform.CompileStatic
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType

import liquibase.changelog.ChangeLogParameters
import liquibase.parser.ChangeLogParserFactory
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
@ToolParameter(index = 0, label = 'SOURCE', arity = '1', description = 'The change log to convert, in the folder of the change logs')
@ToolParameter(index = 1, label = 'FILE', arity = '1', required = false,
        description = 'The Groovy change log to write, in the folder of the change logs; the standard output when not given')
@CompileStatic
class DbmChangelogToGroovyTool implements ApplicationTool, ApplicationContextDatabaseMigrationCommand {

    final String description = 'Converts a changelog file to a Groovy DSL file'

    @Override
    void handle() {
        def srcFilename = args[0]
        if (!srcFilename) {
            throw new DatabaseMigrationException("The $name command requires a source filename")
        }

        def resourceAccessor = createResourceAccessor()

        def parser = ChangeLogParserFactory.instance.getParser(srcFilename, resourceAccessor)
        // the parsers of YAML and JSON changelogs expand expressions with the parameters they are given, and take none for an error
        def databaseChangeLog = parser.parse(srcFilename, new ChangeLogParameters(), resourceAccessor)

        def destFilename = args[1]
        def destChangeLogFile = resolveChangeLogFile(destFilename)
        // asked whether there is one, not whether it exists: a file that does not exist yet is false to Groovy
        if (destChangeLogFile != null) {
            if (!destChangeLogFile.path.endsWith('.groovy')) {
                throw new DatabaseMigrationException("Destination ChangeLogFile ${destChangeLogFile} must be a Groovy file")
            }
            if (destChangeLogFile.exists()) {
                if (hasOption('force')) {
                    destChangeLogFile.delete()
                } else {
                    throw new DatabaseMigrationException("ChangeLogFile ${destChangeLogFile} already exists!")
                }
            }
        }

        def serializer = ChangeLogSerializerFactory.instance.getSerializer('groovy')
        withFileOrSystemOutputStream(destChangeLogFile) { OutputStream out ->
            serializer.write(databaseChangeLog.changeSets, out)
        }

        if (destChangeLogFile != null && hasOption('add')) {
            appendToChangeLog(changeLogFile, destChangeLogFile)
        }
    }

    private static void withFileOrSystemOutputStream(File file, @ClosureParams(value = SimpleType, options = 'java.io.OutputStream') Closure closure) {
        if (file == null) {
            closure.call(System.out)
            return
        }

        if (!file.parentFile.exists()) {
            file.parentFile.mkdirs()
        }
        file.withOutputStream { OutputStream out ->
            closure.call(out)
        }
    }
}
