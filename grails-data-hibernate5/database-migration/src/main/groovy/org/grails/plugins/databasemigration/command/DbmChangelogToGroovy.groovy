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
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import liquibase.parser.ChangeLogParserFactory
import liquibase.serializer.ChangeLogSerializerFactory
import org.grails.plugins.databasemigration.DatabaseMigrationException

@CompileStatic
class DbmChangelogToGroovy implements ScriptDatabaseMigrationCommand {

    @Override
    void handle() {
        def srcFilename = args[0]
        if (!srcFilename) {
            throw new DatabaseMigrationException("The $name command requires a source filename")
        }

        def resourceAccessor = createResourceAccessor()

        def parser = ChangeLogParserFactory.instance.getParser(srcFilename, resourceAccessor)
        def databaseChangeLog = parser.parse(srcFilename, null, resourceAccessor)

        def destFilename = args[1]
        def destChangeLogFile = resolveChangeLogFile(destFilename)
        if (destChangeLogFile) {
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

        if (destChangeLogFile && hasOption('add')) {
            appendToChangeLog(changeLogFile, destChangeLogFile)
        }
    }

    private static void withFileOrSystemOutputStream(File file, @ClosureParams(value = SimpleType, options = "java.io.OutputStream") Closure closure) {
        if (!file) {
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
