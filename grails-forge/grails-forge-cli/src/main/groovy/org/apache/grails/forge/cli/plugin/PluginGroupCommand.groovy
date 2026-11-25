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
package org.apache.grails.forge.cli.plugin

import java.util.concurrent.Callable
import groovy.transform.CompileStatic
import org.grails.forge.cli.command.BaseCommand
import picocli.CommandLine

/**
 * {@code plugin}: the commands that say which Grails plugins there are. {@code plugin list} is
 * {@code list-plugins} and {@code plugin show} is {@code plugin-info}, each one command with two
 * names; {@code plugin search} is the list with the text it is listed by.
 *
 * @since 9.0
 */
@CommandLine.Command(name = PluginGroupCommand.NAME, description = 'Says which Grails plugins there are, and what is known of one',
        subcommands = [PluginListCommand, PluginSearchCommand, PluginShowCommand])
@CompileStatic
class PluginGroupCommand extends BaseCommand implements Callable<Integer> {

    public static final String NAME = 'plugin'

    @Override
    Integer call() throws Exception {
        throw new CommandLine.ParameterException(spec.commandLine(), 'No command specified: plugin list lists the Grails plugins, plugin search lists those ' +
                'that have a text in their name, their coordinates or their description, and plugin show says what is known of one')
    }
}
