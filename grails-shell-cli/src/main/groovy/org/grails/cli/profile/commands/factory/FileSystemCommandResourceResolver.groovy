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

package org.grails.cli.profile.commands.factory

import groovy.transform.CompileStatic

import org.grails.cli.profile.Profile
import org.grails.io.support.PathMatchingResourcePatternResolver
import org.grails.io.support.Resource
import org.grails.io.support.StaticResourceLoader

/**
 * A {@link CommandResourceResolver} that resolves from the file system
 *
 * @author Graeme Rocher
 * @since 3.0
 */
@CompileStatic
class FileSystemCommandResourceResolver implements CommandResourceResolver {

    final Collection<String> matchingFileExtensions

    FileSystemCommandResourceResolver(Collection<String> matchingFileExtensions) {
        this.matchingFileExtensions = matchingFileExtensions
    }

    @Override
    Collection<Resource> findCommandResources(Profile profile) {
        Resource commandsDir = getCommandsDirectory(profile)
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(new StaticResourceLoader(commandsDir))
        if(commandsDir.exists()) {
            Collection<Resource> commandFiles = []
            for(ext in matchingFileExtensions) {
                commandFiles.addAll resolver.getResources("*.$ext")
            }
            commandFiles = commandFiles.sort(false) { Resource file -> file.filename }
            return commandFiles
        }
        return []
    }

    protected Resource getCommandsDirectory(Profile profile) {
        profile.profileDir.createRelative("commands/")
    }
}
