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

import org.springframework.boot.cli.command.CommandException as BootCommandException
import spock.lang.Specification

class CommandExceptionSpec extends Specification {

    def 'constructors retain their messages causes and options'() {
        given:
        def cause = new IllegalStateException('underlying failure')
        def option = CommandException.Option.SHOW_USAGE

        expect:
        new CommandException().message == null
        new CommandException().options.empty
        new CommandException((CommandException.Option[]) null).options.empty
        new CommandException(option).options == [option].toSet()
        new CommandException('failed', option).message == 'failed'
        new CommandException('failed', option).options == [option].toSet()
        new CommandException('failed', cause, option).message == 'failed'
        new CommandException('failed', cause, option).cause.is(cause)
        new CommandException('failed', cause, option).options == [option].toSet()
        new CommandException(cause, option).message == cause.toString()
        new CommandException(cause, option).cause.is(cause)
        new CommandException(cause, option).options == [option].toSet()
    }

    def 'options are deduplicated defensively copied and immutable'() {
        given:
        def options = [CommandException.Option.HIDE_MESSAGE, CommandException.Option.STACK_TRACE,
                       CommandException.Option.HIDE_MESSAGE] as CommandException.Option[]
        def exception = new CommandException('failed', options)

        when:
        options[0] = CommandException.Option.RETHROW

        then:
        exception.options == [CommandException.Option.HIDE_MESSAGE, CommandException.Option.STACK_TRACE].toSet()

        when:
        exception.options.clear()

        then:
        thrown(UnsupportedOperationException)
    }

    def 'specialized exceptions provide command messages and help options'() {
        expect:
        new NoSuchCommandException('unknown').message == "'unknown' is not a valid command. See 'help'."
        new NoSuchCommandException('unknown').options.empty
        new NoHelpCommandArgumentsException().message == null
        new NoHelpCommandArgumentsException().options == [BootCommandException.Option.SHOW_USAGE,
                                                         BootCommandException.Option.HIDE_MESSAGE].toSet()
    }
}
