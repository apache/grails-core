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

import grails.util.GrailsNameUtils
import org.grails.build.parsing.CommandLineParser
import org.grails.cli.GrailsCli
import org.grails.cli.profile.ExecutionContext
import org.grails.config.CodeGenConfig
import org.h2.Driver

abstract class ScriptDatabaseMigrationCommandSpec extends DatabaseMigrationCommandSpec {

    ScriptDatabaseMigrationCommand command

    CodeGenConfig config

    def setup() {
        def configMap = [
            'grails.plugin.databasemigration.changelogLocation': changeLogLocation.canonicalPath,
            'dataSource.url'                                   : 'jdbc:h2:mem:testDb',
            'dataSource.username'                              : 'sa',
            'dataSource.password'                              : '',
            'dataSource.driverClassName'                       : Driver.name,
            'environments.other.dataSource.url'                : 'jdbc:h2:mem:otherDb',
        ]
        config = new CodeGenConfig()
        config.mergeMap(configMap)
        config.mergeMap(configMap, true)

        command = commandClass.newInstance()
        command.config = config
        command.changeLogFile.parentFile.mkdirs()
    }

    abstract protected Class<ScriptDatabaseMigrationCommand> getCommandClass()

    protected ExecutionContext getExecutionContext(String... args) {
        def executionContext = new GrailsCli.ExecutionContextImpl(config)
        executionContext.commandLine = new CommandLineParser().parse(([GrailsNameUtils.getScriptName(GrailsNameUtils.getLogicalName(commandClass.name, 'Command'))] + args.toList()) as String[])
        executionContext
    }
}
