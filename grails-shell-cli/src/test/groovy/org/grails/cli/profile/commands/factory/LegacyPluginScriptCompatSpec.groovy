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

import java.net.URL
import java.net.URLClassLoader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

import org.grails.cli.profile.Command
import org.grails.cli.profile.commands.script.GroovyScriptCommand
import org.grails.io.support.Resource
import spock.lang.Specification
import spock.lang.TempDir

class LegacyPluginScriptCompatSpec extends Specification {

    @TempDir
    Path tempDir

    URLClassLoader pluginClassLoader

    def cleanup() {
        pluginClassLoader?.close()
    }

    // A Grails 7 plugin script must retain its META-INF command name on Grails 8 without source changes after PR #15948.
    void "Grails 7 plugin command scripts are discovered and compiled on Grails 8"() {
        given: "an unchanged Grails 7-style plugin script on the plugin classpath"
        Path commandsDirectory = Files.createDirectories(tempDir.resolve('META-INF').resolve('commands'))
        Files.writeString(commandsDirectory.resolve('audit-quickstart.groovy'), '''
description('Creates audit logging scaffolding') {
    usage 'grails audit-quickstart [DOMAIN CLASS]'
    argument name: 'domainClass', description: 'The audited domain class', required: true
}

if (!args) {
    error 'A domain class name is required'
    return false
}

def auditModel = model(args[0])
render template: template('Audit.groovy'),
       destination: file("grails-app/domain/${auditModel.simpleName}Audit.groovy"),
       model: auditModel
''', StandardCharsets.UTF_8)
        pluginClassLoader = new URLClassLoader([tempDir.toUri().toURL()] as URL[], getClass().classLoader)
        def resolver = new ClasspathCommandResourceResolver(['groovy'])
        resolver.classLoader = pluginClassLoader
        def factory = new ClasspathOnlyGroovyScriptCommandFactory(resolver)

        when: "the plugin command resource is discovered and compiled"
        Collection<Resource> resources = resolver.findCommandResources(null)
        Resource scriptResource = resources.find { it.filename == 'audit-quickstart.groovy' }
        GroovyScriptCommand compiledCommand = factory.compile(scriptResource)
        Collection<Command> discoveredCommands = factory.findCommands(null, false)
        Command discoveredCommand = discoveredCommands.find()

        then: "the legacy script uses the Grails 8 script base class and transform"
        scriptResource != null
        compiledCommand != null
        compiledCommand instanceof GroovyScriptCommand
        compiledCommand.description.description == 'Creates audit logging scaffolding'
        compiledCommand.description.usage == 'grails audit-quickstart [DOMAIN CLASS]'
        compiledCommand.description.arguments*.name == ['domainClass']
        discoveredCommands*.name == ['audit-quickstart']
        discoveredCommand instanceof GroovyScriptCommand
        discoveredCommand.name == 'audit-quickstart'
        discoveredCommand.description.name == 'audit-quickstart'
        discoveredCommand.description.description == 'Creates audit logging scaffolding'
    }

    private static class ClasspathOnlyGroovyScriptCommandFactory extends GroovyScriptCommandFactory {

        private final CommandResourceResolver commandResourceResolver

        ClasspathOnlyGroovyScriptCommandFactory(CommandResourceResolver commandResourceResolver) {
            this.commandResourceResolver = commandResourceResolver
        }

        GroovyScriptCommand compile(Resource resource) {
            readCommandFile(resource)
        }

        @Override
        protected Collection<CommandResourceResolver> getCommandResolvers(boolean inherited) {
            [commandResourceResolver]
        }
    }
}
