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

import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

import org.apache.grails.gradle.model.cli.ToolDescriptor
import spock.lang.Specification
import spock.lang.TempDir

/**
 * A tool is registered in {@code META-INF/grails-cli.factories} under the key of the tool contract,
 * which the compiler writes, and a companion of Grails 8 has its commands under the key of the
 * command contract: the registry reads both, and makes a class registered under both, or by two
 * jars, once.
 */
class ToolRegistryKeysSpec extends Specification {

    @TempDir
    Path tempDir

    private ClassLoader original
    private URLClassLoader loader

    def setupSpec() {
        // the registry makes its instance when its class is initialized: that one is made here, before a jar of a feature is there
        assert ApplicationContextCommandRegistry.instance != null
    }

    def setup() {
        original = Thread.currentThread().contextClassLoader
        KeysSpecBothKeysTool.made = 0
        KeysSpecTwoJarsTool.made = 0
    }

    def cleanup() {
        Thread.currentThread().contextClassLoader = original
        loader?.close()
    }

    def "a jar with the key of Grails 8, one with the key of the tool contract, and one with both give their tools by their names"() {
        given:
        URL oldKey = jar('old-key.jar', "${ToolDescriptor.FACTORIES_KEY_COMMAND}=${KeysSpecOldKeyTool.name}")
        URL newKey = jar('new-key.jar', "${ToolDescriptor.FACTORIES_KEY_TOOL}=${KeysSpecNewKeyTool.name}")
        URL bothKeys = jar('both-keys.jar', "${ToolDescriptor.FACTORIES_KEY_TOOL}=${KeysSpecBothKeysTool.name}\n" +
                "${ToolDescriptor.FACTORIES_KEY_COMMAND}=${KeysSpecBothKeysTool.name},${KeysSpecOldKeyOnlyTool.name}")
        use(oldKey, newKey, bothKeys)

        when:
        ApplicationContextCommandRegistry registry = new ApplicationContextCommandRegistry()

        then:
        registry.findCommand('keys-spec-old-key') instanceof KeysSpecOldKeyTool
        registry.findCommand('keys-spec-new-key') instanceof KeysSpecNewKeyTool
        registry.findCommand('keys-spec-both-keys') instanceof KeysSpecBothKeysTool
        registry.findCommand('keys-spec-old-key-only') instanceof KeysSpecOldKeyOnlyTool

        and: 'a class registered under both keys is made once, and listed once'
        KeysSpecBothKeysTool.made == 1
        registry.findCommands().count { it instanceof KeysSpecBothKeysTool } == 1
    }

    def "a class two jars register, one under each key, is made once"() {
        given:
        use(jar('first.jar', "${ToolDescriptor.FACTORIES_KEY_TOOL}=${KeysSpecTwoJarsTool.name}"),
                jar('second.jar', "${ToolDescriptor.FACTORIES_KEY_COMMAND}=${KeysSpecTwoJarsTool.name}"))

        when:
        ApplicationContextCommandRegistry registry = new ApplicationContextCommandRegistry()

        then:
        registry.findCommand('keys-spec-two-jars') instanceof KeysSpecTwoJarsTool
        KeysSpecTwoJarsTool.made == 1
    }

    def "a standalone tool is found under the key of the tool contract, and a class under any other key is no tool"() {
        given:
        use(jar('standalone.jar', "${ToolDescriptor.FACTORIES_KEY_TOOL}=${KeysSpecStandaloneTool.name}\n" +
                "grails.dev.commands.ApplicationCommand=${KeysSpecOtherKeyTool.name}\n" +
                "org.apache.grails.core.cli.ApplicationTool=${KeysSpecOtherKeyTool.name}"))

        when:
        ApplicationContextCommandRegistry registry = new ApplicationContextCommandRegistry()

        then:
        registry.findCommand('keys-spec-standalone') instanceof KeysSpecStandaloneTool
        registry.findCommand('keys-spec-other-key') == null
    }

    private URL jar(String name, String factories) {
        Path jar = tempDir.resolve(name)
        new JarOutputStream(Files.newOutputStream(jar)).withCloseable { JarOutputStream output ->
            output.putNextEntry(new JarEntry('META-INF/grails-cli.factories'))
            output.write(factories.getBytes('UTF-8'))
            output.closeEntry()
        }
        jar.toUri().toURL()
    }

    private void use(URL... jars) {
        loader = new URLClassLoader(jars, getClass().classLoader)
        Thread.currentThread().contextClassLoader = loader
    }
}

class KeysSpecOldKeyTool implements ApplicationCommand {

    @Override
    boolean handle(ExecutionContext executionContext) {
        true
    }
}

class KeysSpecOldKeyOnlyTool implements ApplicationTool {

    @Override
    boolean handle(ExecutionContext executionContext) {
        true
    }
}

class KeysSpecNewKeyTool implements ApplicationTool {

    @Override
    boolean handle(ExecutionContext executionContext) {
        true
    }
}

class KeysSpecBothKeysTool implements ApplicationTool {

    static int made

    KeysSpecBothKeysTool() {
        made++
    }

    @Override
    boolean handle(ExecutionContext executionContext) {
        true
    }
}

class KeysSpecTwoJarsTool implements ApplicationTool {

    static int made

    KeysSpecTwoJarsTool() {
        made++
    }

    @Override
    boolean handle(ExecutionContext executionContext) {
        true
    }
}

class KeysSpecStandaloneTool implements StandaloneTool {

    @Override
    boolean handle(ExecutionContext executionContext) {
        true
    }
}

class KeysSpecOtherKeyTool implements ApplicationTool {

    @Override
    boolean handle(ExecutionContext executionContext) {
        true
    }
}
