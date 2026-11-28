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

import groovy.json.JsonSlurper
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.Phases

import org.apache.grails.core.cli.ApplicationContextCommandRegistry
import org.apache.grails.core.cli.GrailsTool
import org.apache.grails.gradle.model.cli.ToolDescriptor
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

/**
 * The name of a tool that does not say otherwise is the one its class name gives: the ending
 * {@code Tool} is taken off, and from a class name that does not end with it, the ending
 * {@code Command}. The compiler records that name, the tool has it when it runs, and the registry
 * finds the tool by it.
 */
class ToolNameConventionSpec extends Specification {

    @TempDir
    File tempDir

    @Unroll
    def "the class #className is the tool #name, which it is recorded as, runs as and is found by"() {
        when:
        File classes = compile(className, "class ${className} implements org.apache.grails.core.cli.${type} { ${members} }")

        then: 'the compiler records the name'
        ((Map) new JsonSlurper().parse(new File(classes, ToolDescriptor.location("example.${className}")), 'US-ASCII')).name == name

        and: 'the tool has it when it is made, and the registry finds the tool by it'
        withTools(classes) {
            GrailsTool found = new ApplicationContextCommandRegistry().findCommand(name)
            found?.getClass()?.name == "example.${className}".toString() && found.name == name
        }

        and: 'it is the name the format of a descriptor works out from the class name'
        ToolDescriptor.conventionName("example.${className}") == name

        where:
        className                 | type               || name
        'CreateReportTool'        | 'ApplicationTool'  || 'create-report'
        'CreateReportCommand'     | 'ApplicationTool'  || 'create-report'
        'ToolTool'                | 'ApplicationTool'  || 'tool'
        'RenderTemplatesTool'     | 'StandaloneTool'   || 'render-templates'
        'CreateReportToolCommand' | 'ApplicationTool'  || 'create-report-tool'
        'ToolCommand'             | 'ApplicationTool'  || 'tool'
        'ReportCommandTool'       | 'ApplicationTool'  || 'report-command'
        'ReportBuilder'           | 'ApplicationTool'  || 'report-builder'
        'OldReportCommand'        | 'ApplicationCommand' || 'old-report'
    }

    @Unroll
    def "the class #className gives no name, so it gets no descriptor and keeps its registration"() {
        when:
        File classes = compile(className, "class ${className} implements org.apache.grails.core.cli.ApplicationTool { ${members} }")
        Properties factories = new Properties()
        new File(classes, CommandFactoriesTransformation.CLI_FACTORIES_LOCATION).withInputStream { InputStream input -> factories.load(input) }

        then:
        !new File(classes, ToolDescriptor.location("example.${className}")).exists()
        factories.getProperty(ToolDescriptor.FACTORIES_KEY_TOOL) == "example.${className}".toString()

        where:
        className << ['Tool', 'Command']
    }

    private static String getMembers() {
        'boolean handle(org.apache.grails.core.cli.ExecutionContext executionContext) { true }'
    }

    private File compile(String className, String source) {
        String folder = source.contains('StandaloneTool') ? 'src/standalone/groovy' : 'src/cli/groovy'
        File sourceFile = new File(tempDir, "${folder}/example/${className}.groovy")
        sourceFile.parentFile.mkdirs()
        sourceFile.text = "package example\n${source}"
        File classes = new File(tempDir, 'build/classes/groovy/cli')
        CompilationUnit unit = new CompilationUnit(new CompilerConfiguration(targetDirectory: classes))
        unit.addSource(sourceFile)
        unit.compile(Phases.ALL)
        classes
    }

    // runs the action with the compiled tools on the class path the registry of tools reads
    private static <T> T withTools(File classes, Closure<T> action) {
        Thread thread = Thread.currentThread()
        ClassLoader original = thread.contextClassLoader
        new URLClassLoader([classes.toURI().toURL()] as URL[], GrailsTool.classLoader).withCloseable { URLClassLoader loader ->
            thread.contextClassLoader = loader
            try {
                action.call()
            } finally {
                thread.contextClassLoader = original
            }
        }
    }
}
