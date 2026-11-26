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
package org.apache.grails.scaffolding.cli

import groovy.transform.CompileStatic

import grails.build.logging.ConsoleLogger
import grails.build.logging.GrailsConsole
import org.apache.grails.core.cli.GrailsApplicationCommand
import org.apache.grails.core.cli.StandaloneCommand
import org.grails.io.support.Resource
import org.grails.io.support.SpringIOUtils

/**
 * Installs scaffolding templates that use f:all to render properties
 * Usage: <code>./gradlew runCommand "-Pargs=install-templates"</code>
 *
 * @author Puneet Behl
 * @since 5.0.0
 */
@CompileStatic
class InstallTemplatesCommand implements GrailsApplicationCommand, StandaloneCommand, SkipBootstrap, CommandLineHelper {

    @Delegate ConsoleLogger consoleLogger = GrailsConsole.getInstance()

    private static final String TEMPLATES_DIRECTORY = 'src/main/templates/scaffolding'

    final String description = 'Installs scaffolding templates that use f:all to render properties'

    @Override
    boolean handle() {
        try {
            mkdir(TEMPLATES_DIRECTORY)
            final File directory = file(TEMPLATES_DIRECTORY)
            Set<String> copied = []
            resources('classpath*:META-INF/templates/scaffolding/*').each { Resource r ->
                final String name = r.filename
                if (!name || r.URL.toString().endsWith('/') || !copied.add(name)) {
                    return
                }
                File to = new File(directory, name)
                SpringIOUtils.copy(r, to)
                consoleLogger.verbose("Copied ${name} to location ${to.canonicalPath}")
            }
            consoleLogger.verbose('Templates installation complete')
            return SUCCESS
        } catch (e) {
            consoleLogger.error(e.message, e)
            return FAILURE
        }
    }
}
