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
package org.apache.grails.core.cli

import java.nio.file.Path

import groovy.json.JsonSlurper

import spock.lang.Specification
import spock.lang.TempDir

class ApplicationCommandManifestWriterSpec extends Specification {

    @TempDir
    Path tempDir

    private ClassLoader originalContextClassLoader
    private URLClassLoader factoryClassLoader

    void setup() {
        originalContextClassLoader = Thread.currentThread().contextClassLoader
    }

    void cleanup() {
        Thread.currentThread().contextClassLoader = originalContextClassLoader
        factoryClassLoader?.close()
    }

    void 'the manifest lists the commands on the classpath, sorted by name, with their descriptions'() {
        given:
        declareCommands(UndescribedManifestCommand, DescribedManifestCommand)

        when:
        Map manifest = writeManifest()

        then:
        manifest.version == ApplicationCommandManifestWriter.FORMAT_VERSION

        and: 'every command the registry finds, in name order'
        List<String> names = manifest.commands*.name
        names == names.toSorted()
        names.containsAll(['described-manifest', 'undescribed-manifest'])

        and:
        with(command(manifest, 'described-manifest')) {
            description == 'Sets up the users of the application'
            className == DescribedManifestCommand.name
        }

        and: "a command without a description of its own is described by the trait's default, its name"
        with(command(manifest, 'undescribed-manifest')) {
            description == 'undescribed-manifest'
            arguments == []
            options == []
        }
    }

    void 'the manifest describes the declared arguments in the order they are given and the declared options'() {
        given:
        declareCommands(DescribedManifestCommand)

        when:
        Map described = command(writeManifest(), 'described-manifest')

        then:
        described.arguments == [
                [name: 'package', description: 'The package of the domain classes', required: true, values: []],
                [name: 'user-class-name', description: 'The name of the user class', required: false, values: []]
        ]

        and:
        described.options == [
                [name: 'groupClassName', description: 'Also generate role group classes', flag: false, values: []],
                [name: 'uiOnly', description: 'Configure without generating classes', flag: true, values: []],
                [name: 'format', description: '', flag: false, values: ['json', 'yaml']]
        ]
    }

    void 'a command may declare a single argument and a single option'() {
        given:
        declareCommands(SingleDeclarationManifestCommand)

        when:
        Map described = command(writeManifest(), 'single-declaration-manifest')

        then:
        described.arguments == [[name: 'domain-class', description: '', required: true, values: ['Book', 'Author']]]
        described.options == [[name: 'force', description: '', flag: true, values: []]]
    }

    void 'a direct declaration and an explicit container are read together, in the order they are declared: #type.simpleName'() {
        given:
        declareCommands(type)

        when:
        Map described = command(writeManifest(), name)

        then:
        described.arguments*.name == arguments
        described.options*.name == options

        where:
        type                                || name                           | arguments           | options
        DirectFirstManifestCommand          || 'direct-first-manifest'        | ['a', 'b', 'c']     | ['x', 'y', 'z']
        ContainerFirstManifestCommand       || 'container-first-manifest'     | ['b', 'c', 'a']     | ['y', 'z', 'x']
    }

    void 'a command whose description fails with #failure.simpleName is listed with its name for a description, and the others as they are'() {
        given: 'a command whose description needs the application context, which the writer does not start'
        FailingDescriptionManifestCommand.failure = failure
        declareCommands(FailingDescriptionManifestCommand, DescribedManifestCommand)

        when:
        Map manifest = writeManifest()

        then:
        command(manifest, 'failing-description-manifest').description == 'failing-description-manifest'
        command(manifest, 'described-manifest').description == 'Sets up the users of the application'

        cleanup:
        FailingDescriptionManifestCommand.failure = null

        where:
        failure << [IllegalStateException, AssertionError, NoClassDefFoundError]
    }

    void 'the directory of the manifest is created when it does not exist'() {
        given:
        declareCommands(UndescribedManifestCommand)
        File manifestFile = tempDir.resolve('build/grails/commands.json').toFile()

        when:
        ApplicationCommandManifestWriter.main(manifestFile.path)

        then:
        manifestFile.isFile()
    }

    void 'the writer needs the path of the file to write'() {
        when:
        ApplicationCommandManifestWriter.main(args as String[])

        then:
        IllegalArgumentException e = thrown()
        e.message == 'Expected the path of the manifest file to write as the only argument'

        where:
        args << [[], ['one.json', 'two.json']]
    }

    private void declareCommands(Class<?>... commandTypes) {
        File metaInf = tempDir.resolve('classes/META-INF').toFile()
        assert metaInf.mkdirs()
        new File(metaInf, 'grails-cli.factories').text = "${ApplicationCommand.name}=${commandTypes*.name.join(',')}"
        factoryClassLoader = new URLClassLoader([tempDir.resolve('classes').toUri().toURL()] as URL[], getClass().classLoader)
        Thread.currentThread().contextClassLoader = factoryClassLoader
    }

    private Map writeManifest() {
        File manifestFile = tempDir.resolve('commands.json').toFile()
        ApplicationCommandManifestWriter.main(manifestFile.path)
        (Map) new JsonSlurper().parse(manifestFile)
    }

    private static Map command(Map manifest, String name) {
        (Map) manifest.commands.find { it.name == name }
    }
}

class UndescribedManifestCommand implements ApplicationCommand {

    @Override
    boolean handle(ExecutionContext executionContext) {
        true
    }
}

@CommandParameter(name = 'package', description = 'The package of the domain classes')
@CommandParameter(name = 'user-class-name', description = 'The name of the user class', required = false)
@CommandOption(name = 'groupClassName', description = 'Also generate role group classes')
@CommandOption(name = 'uiOnly', description = 'Configure without generating classes', flag = true)
@CommandOption(name = 'format', values = ['json', 'yaml'])
class DescribedManifestCommand implements ApplicationCommand {

    final String description = 'Sets up the users of the application'

    @Override
    boolean handle(ExecutionContext executionContext) {
        true
    }
}

@CommandParameter(name = 'domain-class', values = ['Book', 'Author'])
@CommandOption(name = 'force', flag = true)
class SingleDeclarationManifestCommand implements ApplicationCommand {

    @Override
    boolean handle(ExecutionContext executionContext) {
        true
    }
}

@CommandParameter(name = 'a')
@CommandParameters([@CommandParameter(name = 'b'), @CommandParameter(name = 'c')])
@CommandOption(name = 'x')
@CommandOptions([@CommandOption(name = 'y'), @CommandOption(name = 'z')])
class DirectFirstManifestCommand implements ApplicationCommand {

    @Override
    boolean handle(ExecutionContext executionContext) {
        true
    }
}

@CommandParameters([@CommandParameter(name = 'b'), @CommandParameter(name = 'c')])
@CommandParameter(name = 'a')
@CommandOptions([@CommandOption(name = 'y'), @CommandOption(name = 'z')])
@CommandOption(name = 'x')
class ContainerFirstManifestCommand implements ApplicationCommand {

    @Override
    boolean handle(ExecutionContext executionContext) {
        true
    }
}

class FailingDescriptionManifestCommand implements ApplicationCommand {

    static Class<? extends Throwable> failure

    @Override
    String getDescription() {
        throw failure.getConstructor(String).newInstance('The description needs the application context')
    }

    @Override
    boolean handle(ExecutionContext executionContext) {
        true
    }
}
