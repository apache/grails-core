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
 * {@code list-plugins}: the Grails plugins there are, each with its name, its coordinates, the version
 * an application of this Grails gets and what it is for. See {@link PluginsCommand} for where the
 * plugins are known from.
 *
 * @since 9.0
 */
@CommandLine.Command(name = ListPluginsCommand.NAME, description = [
        'Lists the Grails plugins: those of this release of Grails, and those of the plugin index of Grails, which is read from the network.',
        'Each has its name, its coordinates, the latest version for this Grails and what it is for; @|bold plugin-info|@ says more of one.',
        'The command is also there as @|bold plugin list|@.'])
@CompileStatic
class ListPluginsCommand extends PluginListingCommand {

    public static final String NAME = 'list-plugins'

    @CommandLine.Parameters(arity = '0..1', paramLabel = 'TEXT',
            description = 'Lists only the plugins that have this text in their name, their coordinates or their description, in whichever case.')
    String filter

    /**
     * @param context the Forge application of the command line, see {@link PluginsCommand#PluginsCommand(ForgeContext)}
     */
    ListPluginsCommand(ForgeContext context) {
        super(context)
    }

    @Override
    protected String listedBy() {
        return filter
    }

    @Override
    protected String showCommand() {
        return PluginInfoCommand.NAME
    }
}
