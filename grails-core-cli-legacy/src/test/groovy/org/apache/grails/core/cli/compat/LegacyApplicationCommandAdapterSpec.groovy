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
package org.apache.grails.core.cli.compat

import grails.dev.commands.ApplicationCommand
import grails.dev.commands.ExecutionContext as LegacyExecutionContext
import org.apache.grails.core.cli.ExecutionContext
import org.grails.build.parsing.CommandLine
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import spock.lang.Specification

class LegacyApplicationCommandAdapterSpec extends Specification {

    def "forwards command operations to the legacy command"() {
        given:
        TestLegacyCommand legacyCommand = new TestLegacyCommand()
        LegacyApplicationCommandAdapter adapter = new LegacyApplicationCommandAdapter(legacyCommand)
        ConfigurableApplicationContext applicationContext = Mock()
        CommandLine commandLine = Mock()

        when:
        adapter.applicationContext = applicationContext
        boolean result = adapter.handle(new ExecutionContext(commandLine))

        then:
        adapter.name == 'legacy-command'
        adapter.description == 'Legacy command description'
        adapter.applicationContext.is(applicationContext)
        adapter.target.is(legacyCommand)
        result
        legacyCommand.applicationContext.is(applicationContext)
        legacyCommand.executionContext instanceof LegacyExecutionContext
        legacyCommand.executionContext.commandLine.is(commandLine)
    }

    def "propagates the order of a legacy command that implements Ordered"() {
        expect:
        new LegacyApplicationCommandAdapter(new OrderedInterfaceLegacyCommand()).order == 42
    }

    def "resolves the order of a legacy command declared with the Order annotation"() {
        expect:
        new LegacyApplicationCommandAdapter(new OrderAnnotatedLegacyCommand()).order == 7
    }

    def "prefers the Ordered interface over the Order annotation when a legacy command has both"() {
        expect:
        new LegacyApplicationCommandAdapter(new DoublyOrderedLegacyCommand()).order == 5
    }

    def "defaults an unordered legacy command to lowest precedence"() {
        expect:
        new LegacyApplicationCommandAdapter(new TestLegacyCommand()).order == Ordered.LOWEST_PRECEDENCE
    }

    private static class TestLegacyCommand implements ApplicationCommand {

        String name = 'legacy-command'
        String description = 'Legacy command description'
        LegacyExecutionContext executionContext

        @Override
        boolean handle(LegacyExecutionContext executionContext) {
            this.executionContext = executionContext
            true
        }
    }

    private static class OrderedInterfaceLegacyCommand extends TestLegacyCommand implements Ordered {

        @Override
        int getOrder() {
            42
        }
    }

    @Order(7)
    private static class OrderAnnotatedLegacyCommand extends TestLegacyCommand {
    }

    @Order(9)
    private static class DoublyOrderedLegacyCommand extends TestLegacyCommand implements Ordered {

        @Override
        int getOrder() {
            5
        }
    }
}
