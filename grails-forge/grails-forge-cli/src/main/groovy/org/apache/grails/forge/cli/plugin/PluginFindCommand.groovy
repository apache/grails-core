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

import groovy.transform.CompileStatic
import org.grails.forge.cli.ForgeContext
import picocli.CommandLine

/**
 * {@code plugin find}: the plugins {@code plugin list} lists that have a text in their name, their
 * coordinates or their description. It is {@code plugin list} with the text it lists by, which this
 * command is not run without: the text is an argument the command line requires.
 *
 * @since 9.0
 */
@CommandLine.Command(name = PluginFindCommand.NAME, description = [
        'Lists the Grails plugins that have a text in their name, their coordinates or their description, in whichever case: ' +
                'those of this release of Grails, and those of the plugin index of Grails, which is read from the network.',
        'It is @|bold plugin list|@ with the text to list by; @|bold plugin show|@ says more of one plugin.'])
@CompileStatic
class PluginFindCommand extends PluginListingCommand {

    public static final String NAME = 'find'

    @CommandLine.Parameters(arity = '1', paramLabel = 'TEXT',
            description = 'The text the plugins are listed by: one line, which a plugin has in its name, its coordinates or its description, in whichever case.')
    String text

    /**
     * @param context the Forge application of the command line, see {@link PluginsCommand#PluginsCommand(ForgeContext)}
     */
    PluginFindCommand(ForgeContext context) {
        super(context)
    }

    @Override
    protected int run() {
        if (text.trim().isEmpty()) {
            throw new CommandLine.ParameterException(spec.commandLine(), 'Give the text to find the plugins by: plugin list lists every plugin')
        }
        return super.run()
    }

    @Override
    protected String listedBy() {
        return text
    }

    @Override
    protected String showCommand() {
        return "${PluginGroupCommand.NAME} ${PluginShowCommand.NAME}"
    }
}
