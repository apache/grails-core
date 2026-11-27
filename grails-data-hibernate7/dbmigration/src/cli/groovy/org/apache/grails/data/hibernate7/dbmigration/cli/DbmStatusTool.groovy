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

import liquibase.Liquibase

import org.apache.grails.core.cli.ApplicationTool
import org.apache.grails.core.cli.ToolHints
import org.apache.grails.core.cli.ToolOption
import org.apache.grails.core.cli.ToolParameter

// It never changes the database, and writes a file only when the caller names one: read-only, as dbm-validate is
@ToolHints(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
@DatabaseMigrationOptions
@ToolOption(names = ['--verbose'], arity = '1', label = 'TRUE-OR-FALSE', choices = ['true', 'false'], caseInsensitive = true, defaultValue = 'true',
        description = 'Lists the change sets that have not run when true, and only counts them when false')
@ToolParameter(index = 0, label = 'FILE', arity = '1', required = false,
        description = 'The file to write the status to; the standard output when not given')
@CompileStatic
class DbmStatusTool implements ApplicationTool, ApplicationContextDatabaseMigrationCommand {

    final String description = 'Outputs count or list of unrun change sets to STDOUT or a file'

    void handle() {
        def filename = args[0]
        def verbose = hasOption('verbose') ? Boolean.parseBoolean(optionValue('verbose')) as Boolean : true

        withLiquibase { Liquibase liquibase ->
            withFileOrSystemOutWriter(filename) { Writer writer ->
                liquibase.reportStatus(verbose, contexts, writer)
            }
        }
    }
}
