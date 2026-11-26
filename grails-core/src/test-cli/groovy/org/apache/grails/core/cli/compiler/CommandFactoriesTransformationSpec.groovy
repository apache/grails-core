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
package org.apache.grails.core.cli.compiler

import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.Phases

import org.apache.grails.core.cli.ApplicationCommandTargetAware
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

/**
 * Commands shipped in a companion cli artifact compile from the {@code cli} source set
 * ({@code src/cli/groovy}), which is not a standard project-source location — the transformation
 * must still register them in {@code META-INF/grails-cli.factories}.
 */
class CommandFactoriesTransformationSpec extends Specification {

    @Unroll
    def "recognises #path as a cli source: #expected"() {
        expect:
        CommandFactoriesTransformation.isCliSource(new URI("file://${path}").toURL()) == expected

        where:
        path                                                                    || expected
        '/work/my-plugin/src/cli/groovy/com/example/cli/AuditCommand.groovy'    || true
        '/work/my-plugin/src/cli/java/com/example/cli/AuditCommand.java'        || true
        '/work/my-plugin/src/standalone/groovy/com/example/RenderTool.groovy'   || true
        '/work/my-plugin/src/standalone/java/com/example/RenderTool.java'       || true
        '/work/my-plugin/src/standalone/resources/META-INF/grails-cli.factories' || false
        '/work/my-plugin/src/main/groovy/com/example/RuntimeClass.groovy'       || false
        '/work/my-plugin/src/cli/resources/META-INF/grails-cli.factories'       || false
        '/work/my-plugin/grails-app/commands/example/BackupCommand.groovy'      || false
        '/work/clipboard/src/climate/groovy/com/example/NotCliCommand.groovy'   || false
    }

    def "does not register target-aware command adapters as command factories"() {
        expect:
        !CommandFactoriesTransformation.shouldRegisterCommand(ClassHelper.make(TargetAwareTestAdapter))
    }

    def "a command compiled again as a target-aware adapter leaves grails-cli.factories"() {
        given: 'two commands compiled into the same output'
        File sourceFile = new File(tempDir, 'src/cli/groovy/example/BackupCommand.groovy')
        File targetDir = new File(tempDir, 'build/classes/groovy/cli')
        compile(sourceFile, command('BackupCommand', ''), targetDir)
        compile(new File(tempDir, 'src/cli/groovy/example/RestoreCommand.groovy'), command('RestoreCommand', ''), targetDir)

        when: 'incremental compilation recompiles only the edited one, now an adapter'
        compile(sourceFile, command('BackupCommand', ', org.apache.grails.core.cli.ApplicationCommandTargetAware',
                'Object getTarget() { null }'), targetDir)

        then:
        cliFactories(targetDir).getProperty(CommandFactoriesTransformation.GRAILS_TOOL_CLASS.name) == 'example.RestoreCommand'
    }

    def "a standalone tool is registered under the key of the tool contract, beside one registered by hand in src/standalone/resources"() {
        given: 'a registration written by hand, where the resources of the standalone source set are'
        File handWritten = new File(tempDir, 'src/standalone/resources/META-INF/grails-cli.factories')
        handWritten.parentFile.mkdirs()
        handWritten.text = "${CommandFactoriesTransformation.GRAILS_TOOL_CLASS.name}=example.ReportJavaTool\n"
        File targetDir = new File(tempDir, 'build/classes/groovy/standalone')

        when:
        compile(new File(tempDir, 'src/standalone/groovy/example/RenderTool.groovy'), '''
            package example

            class RenderTool implements org.apache.grails.core.cli.StandaloneTool {
                boolean handle(org.apache.grails.core.cli.ExecutionContext executionContext) { true }
            }
        ''', targetDir)

        then:
        cliFactories(targetDir).getProperty(CommandFactoriesTransformation.GRAILS_TOOL_CLASS.name).tokenize(',').sort() ==
                ['example.RenderTool', 'example.ReportJavaTool']
        new File(targetDir, 'META-INF/grails-tools/example.RenderTool.json').text.contains('"target":"standalone"')
    }

    @TempDir
    File tempDir

    private static String command(String className, String extraInterfaces, String extraMembers = '') {
        """
            package example

            class ${className} implements org.apache.grails.core.cli.ApplicationCommand${extraInterfaces} {
                boolean handle(org.apache.grails.core.cli.ExecutionContext executionContext) { true }
                ${extraMembers}
            }
        """
    }

    private static Properties cliFactories(File targetDirectory) {
        Properties factories = new Properties()
        new File(targetDirectory, CommandFactoriesTransformation.CLI_FACTORIES_LOCATION).withInputStream { factories.load(it) }
        factories
    }

    private static void compile(File sourceFile, String source, File targetDirectory) {
        sourceFile.parentFile.mkdirs()
        sourceFile.text = source
        CompilationUnit unit = new CompilationUnit(new CompilerConfiguration(targetDirectory: targetDirectory))
        unit.addSource(sourceFile)
        unit.compile(Phases.CANONICALIZATION)
    }
}

class TargetAwareTestAdapter implements ApplicationCommandTargetAware {

    @Override
    Object getTarget() {
        null
    }
}
