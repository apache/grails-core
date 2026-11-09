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
package org.grails.cli.command

import joptsimple.OptionException
import joptsimple.OptionSet
import org.springframework.boot.cli.command.options.OptionHandler
import org.springframework.boot.cli.command.status.ExitStatus
import spock.lang.Specification

class OptionParsingCommandSpec extends Specification {

    def 'command exposes its metadata and delegates help and execution to its option handler'() {
        given:
        def handler = new GreetingHandler()
        def command = new GreetingCommand(handler)

        when:
        def result = command.run('--name', 'Grails', 'extra')

        then:
        result == ExitStatus.OK
        handler.name == 'Grails'
        handler.arguments == ['extra']
        command.name == 'greet'
        command.description == 'Greet a user'
        command.help == handler.help
        command.help.contains('User name')
        command.optionsHelp == handler.optionsHelp
        !command.optionsHelp.empty
    }

    def 'invalid options fail before the handler executes'() {
        given:
        def handler = new GreetingHandler()
        def command = new GreetingCommand(handler)

        when:
        command.run('--unknown')

        then:
        thrown(OptionException)
        handler.arguments == null
    }

    private static class GreetingCommand extends OptionParsingCommand {
        GreetingCommand(OptionHandler handler) {
            super('greet', 'Greet a user', handler)
        }
    }

    private static class GreetingHandler extends OptionHandler {
        String name
        List<?> arguments

        @Override
        protected void options() {
            option('name', 'User name').withRequiredArg()
        }

        @Override
        protected ExitStatus run(OptionSet options) {
            name = options.valueOf('name')
            arguments = options.nonOptionArguments()
            ExitStatus.OK
        }
    }
}
