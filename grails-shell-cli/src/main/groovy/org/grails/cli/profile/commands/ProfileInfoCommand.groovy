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
package org.grails.cli.profile.commands

import grails.build.logging.GrailsConsole
import grails.config.ConfigMap
import grails.util.BuildSettings
import groovy.transform.CompileStatic
import org.grails.cli.profile.Command
import org.grails.cli.profile.CommandDescription
import org.grails.cli.profile.ExecutionContext
import org.grails.cli.profile.Feature
import org.grails.cli.profile.Profile
import org.grails.cli.profile.ProfileRepository
import org.grails.cli.profile.ProfileRepositoryAware
import org.grails.cli.profile.ProjectContext
import org.grails.config.CodeGenConfig


/**
 * A command to find out information about the given profile
 *
 * @author Graeme Rocher
 * @since 3.1
 */
@CompileStatic
class ProfileInfoCommand extends ArgumentCompletingCommand implements ProfileRepositoryAware {

    public static final String NAME = 'profile-info'

    final String name = NAME
    final CommandDescription description = new CommandDescription(name, "Display information about a given profile")

    ProfileRepository profileRepository

    ProfileInfoCommand() {
        description.argument(name:"Profile Name", description: "The name or coordinates of the profile", required:true)
    }

    void setProfileRepository(ProfileRepository profileRepository) {
        this.profileRepository = profileRepository
    }

    @Override
    boolean handle(ExecutionContext executionContext) {
        def console = executionContext.console
        if(profileRepository == null) {
            console.error("No profile repository provided")
            return false
        }
        else {

            def profileName = executionContext.commandLine.remainingArgs[0]

            def profile = profileRepository.getProfile(profileName)
            if(profile == null) {
                console.error("Profile not found for name [$profileName]")
            }
            else {
                console.log("Profile: ${profile.name}")
                console.log('--------------------')
                console.log(profile.description)
                console.log('')
                console.log('Provided Commands:')
                console.log('--------------------')
                Iterable<Command> commands = findCommands(profile, console).toUnique { Command c -> c.name}

                for(cmd in commands) {
                    def description = cmd.description
                    console.log("* ${description.name} - ${description.description}")
                }
                console.log('')
                console.log('Provided Features:')
                console.log('--------------------')
                def features = profile.features

                for(feature in features) {
                    console.log("* ${feature.name} - ${feature.description}")
                }
            }
        }
        return true
    }

    protected Iterable<Command> findCommands(Profile profile, GrailsConsole console) {
        def commands = profile.getCommands(new ProjectContext() {
            @Override
            GrailsConsole getConsole() {
                console
            }

            @Override
            File getBaseDir() {
                return new File(".")
            }

            @Override
            ConfigMap getConfig() {
                return new CodeGenConfig()
            }

            @Override
            String navigateConfig(String... path) {
                return config.navigate(path)
            }

            @Override
            def <T> T navigateConfigForType(Class<T> requiredType, String... path) {
                return (T) config.navigate(path)
            }
        })
        commands
    }
}
