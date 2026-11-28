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
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.codehaus.groovy.control.Phases
import org.codehaus.groovy.control.messages.Message
import org.codehaus.groovy.control.messages.SyntaxErrorMessage
import org.codehaus.groovy.control.messages.WarningMessage

import org.apache.grails.gradle.model.cli.ToolDescriptor

/**
 * Compiles the source of a tool as the build of a project compiles it, with the transformation of
 * grails-core-cli, and says what came of it: the errors, the warnings, what the compiler wrote on the
 * standard error, and the descriptors it wrote.
 */
class ToolCompilation {

    /** The source that was compiled. */
    File source

    /** The directory the classes and the descriptors are written to. */
    File classes

    /** The errors, without where they are. */
    List<String> errors = []

    /** The warnings of the compilation. */
    List<String> warnings = []

    /** What the compiler wrote on the standard error. */
    String err

    /**
     * @param directory the directory of the project
     * @param path where the source is in it, as {@code src/cli/groovy/example/ReportTool.groovy}
     * @param source the source, without its package, which is {@code example}
     * @param classpath more directories of classes
     * @param warningLevel the level of the warnings
     * @return what came of it
     */
    static ToolCompilation compile(File directory, String path, String source, List<File> classpath = [],
                                   int warningLevel = WarningMessage.LIKELY_ERRORS, int phase = Phases.CANONICALIZATION) {
        File sourceFile = new File(directory, path)
        sourceFile.parentFile.mkdirs()
        sourceFile.text = "package example\n${source}"
        File classes = new File(directory, "build/classes/${path.replace('/', '-')}")
        CompilationUnit unit = new CompilationUnit(new CompilerConfiguration(targetDirectory: classes, warningLevel: warningLevel,
                classpathList: classpath*.absolutePath, disabledGlobalASTTransformations: [
                'org.grails.compiler.injection.GlobalGrailsClassInjectorTransformation',
                'org.grails.compiler.injection.GlobalImportTransformation',
        ] as Set))
        unit.addSource(sourceFile)
        ByteArrayOutputStream err = new ByteArrayOutputStream()
        PrintStream original = System.err
        List<String> errors = []
        System.err = new PrintStream(err, true, 'UTF-8')
        try {
            unit.compile(phase)
        } catch (MultipleCompilationErrorsException e) {
            errors = e.errorCollector.errors.collect { Message message -> ((SyntaxErrorMessage) message).cause.originalMessage }
        } finally {
            System.err = original
        }
        new ToolCompilation(source: sourceFile, classes: classes, errors: errors,
                warnings: (unit.errorCollector.warnings ?: []).collect { WarningMessage warning -> warning.message },
                err: err.toString('UTF-8'))
    }

    /**
     * @param className the name of a tool class
     * @return the descriptor written for it, as JSON
     */
    File descriptorFile(String className) {
        new File(classes, ToolDescriptor.location(className))
    }

    /**
     * @param className the name of a tool class
     * @return its descriptor, which the reader of a descriptor takes, or {@code null} when it has none
     */
    Map descriptor(String className) {
        File file = descriptorFile(className)
        if (!file.isFile()) {
            return null
        }
        ToolDescriptor.read(className, file.bytes)
        (Map) new JsonSlurper().parse(file, 'US-ASCII')
    }
}
