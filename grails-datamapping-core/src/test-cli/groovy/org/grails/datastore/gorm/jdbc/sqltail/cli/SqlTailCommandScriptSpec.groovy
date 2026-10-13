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
package org.grails.datastore.gorm.jdbc.sqltail.cli

import grails.build.logging.GrailsConsole
import org.grails.build.parsing.CommandLineParser
import org.grails.cli.GrailsCli
import org.grails.cli.profile.Command
import org.grails.cli.profile.commands.factory.ClasspathCommandResourceResolver
import org.grails.cli.profile.commands.factory.CommandResourceResolver
import org.grails.cli.profile.commands.factory.GroovyScriptCommandFactory
import org.grails.cli.profile.commands.script.GroovyScriptCommand
import org.grails.config.CodeGenConfig
import org.grails.datastore.gorm.jdbc.sqltail.SqlTailRecord
import spock.lang.Specification
import spock.lang.TempDir

/**
 * Compiles and runs the sql-tail command script the way the shell does.
 */
class SqlTailCommandScriptSpec extends Specification {

    @TempDir
    File tempDir

    void cleanup() {
        // the console is a JVM-wide singleton the script reaches through the shell
        GrailsConsole.removeInstance()
    }

    void "the sql-tail script is discovered on the cli classpath and describes its options"() {
        when:
        GroovyScriptCommand command = discover()

        then:
        command.name == 'sql-tail'
        command.description.description == 'Shows the SQL statements the running application executes, by the page that runs them'
        command.description.usage.startsWith('grails sql-tail ')
        command.description.flags*.name == ['statements', 'stream', 'page', 'top', 'all', 'once', 'file']
    }

    void "the script reads a capture once and reports whether there was one"() {
        given:
        File capture = new File(tempDir, 'capture.log')
        capture.text = new SqlTailRecord(timestamp: 1L, dataSource: 'DEFAULT', requestId: 'r1', page: 'book/show',
                request: 'GET /book/show/1', sql: 'select 1').toLine() + '\n'

        expect:
        run('sql-tail', '--once', "--file=${capture.path}".toString())
        !run('sql-tail', '--once', "--file=${new File(tempDir, 'missing.log').path}".toString())
    }

    private static boolean run(String... args) {
        GroovyScriptCommand command = discover()
        GrailsCli.ExecutionContextImpl context = new GrailsCli.ExecutionContextImpl(new CodeGenConfig())
        context.commandLine = new CommandLineParser().parse(args)
        command.handle(context)
    }

    private static GroovyScriptCommand discover() {
        Collection<Command> commands = new ClasspathOnlyGroovyScriptCommandFactory(resolver()).findCommands(null, false)
        Command command = commands.find { it.name == 'sql-tail' }
        assert command instanceof GroovyScriptCommand
        (GroovyScriptCommand) command
    }

    private static CommandResourceResolver resolver() {
        new ClasspathCommandResourceResolver(['groovy'])
    }

    private static class ClasspathOnlyGroovyScriptCommandFactory extends GroovyScriptCommandFactory {

        private final CommandResourceResolver commandResourceResolver

        ClasspathOnlyGroovyScriptCommandFactory(CommandResourceResolver commandResourceResolver) {
            this.commandResourceResolver = commandResourceResolver
        }

        @Override
        protected Collection<CommandResourceResolver> getCommandResolvers(boolean inherited) {
            [commandResourceResolver]
        }
    }
}
