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
package org.grails.plugins.databasemigration.command

import groovy.transform.CompileStatic
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType

import liquibase.Liquibase

import grails.dev.commands.ApplicationCommand

@CompileStatic
class DbmListLocksCommand implements ApplicationCommand, ApplicationContextDatabaseMigrationCommand {

    final String description = 'Lists who currently has locks on the database changelog to STDOUT or a file'

    void handle() {
        def filename = args[0]

        withLiquibase { Liquibase liquibase ->
            withFilePrintStreamOrSystemOut(filename) { PrintStream printStream ->
                liquibase.reportLocks(printStream)
            }
        }
    }

    private static void withFilePrintStreamOrSystemOut(String filename, @ClosureParams(value = SimpleType, options = "java.io.PrintStream") Closure closure) {
        if (!filename) {
            closure.call(System.out)
            return
        }

        def outputFile = new File(filename)
        if (!outputFile.parentFile.exists()) {
            outputFile.parentFile.mkdirs()
        }
        outputFile.withOutputStream { OutputStream out ->
            closure.call(new PrintStream(out))
        }
    }
}
