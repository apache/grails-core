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

import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.control.messages.SyntaxErrorMessage
import org.codehaus.groovy.control.messages.WarningMessage
import org.codehaus.groovy.syntax.SyntaxException
import org.codehaus.groovy.syntax.Token

import org.apache.grails.gradle.model.cli.ToolDescriptor

/**
 * What a tool has to be, which the compiler checks where it writes the descriptor of the tool: a tool
 * is either an {@code ApplicationTool} or a {@code StandaloneTool}, its source is in the folder of its
 * kind, and whether it starts the application is a constant. A class that does otherwise does not
 * compile. A class that implements a deprecated type compiles, with a warning.
 *
 * <p>Nothing of this is checked for a source in a folder of tests ({@link ToolSource#isExempt()}),
 * and the compiler does not see a source without a file.</p>
 *
 * @since 9.0
 */
@PackageScope
@CompileStatic
final class ToolRules {

    static final String APPLICATION_TOOL = 'org.apache.grails.core.cli.ApplicationTool'

    static final String STANDALONE_TOOL = 'org.apache.grails.core.cli.StandaloneTool'

    // the deprecated types a class implements by their names, which the warning says
    static final Map<String, String> DEPRECATED_TYPES = [
            'org.apache.grails.core.cli.ApplicationCommand': 'ApplicationCommand',
            'org.apache.grails.core.cli.GrailsApplicationCommand': 'GrailsApplicationCommand',
    ].asImmutable()

    private ToolRules() {
    }

    /**
     * @param classNode a class
     * @return whether the class, a class it extends or a type it has is {@code ApplicationTool}
     */
    static boolean isApplicationTool(ClassNode classNode) {
        isMarked(classNode, APPLICATION_TOOL)
    }

    /**
     * @param classNode a class
     * @return whether the class, a class it extends or a type it has is {@code StandaloneTool}
     */
    static boolean isStandaloneTool(ClassNode classNode) {
        isMarked(classNode, STANDALONE_TOOL)
    }

    /**
     * Checks a tool the compiler registers, and makes what it does wrong an error of the compilation.
     *
     * @param classNode the tool class
     * @param located where its source is
     * @param source the source
     */
    static void check(ClassNode classNode, ToolSource located, SourceUnit source) {
        if (located.exempt) {
            return
        }
        String name = classNode.nameWithoutPackage
        boolean application = isApplicationTool(classNode)
        boolean standalone = isStandaloneTool(classNode)
        if (application && standalone) {
            error(source, classNode, "${name} implements ApplicationTool and StandaloneTool: a tool is one of the two.")
            return
        }
        if (!application && !standalone) {
            error(source, classNode, "${name} is neither an ApplicationTool nor a StandaloneTool, and a tool is one of the two: " +
                    'an ApplicationTool, in src/cli/groovy, is run by the build with the classes of the project; ' +
                    'a StandaloneTool, in src/standalone/groovy, is run as a process of its own without them.')
            return
        }
        if (standalone && located.folder in [ToolSource.Folder.CLI, ToolSource.Folder.LEGACY, ToolSource.Folder.GRAILS_APP, ToolSource.Folder.MAIN]) {
            error(source, classNode, "${name} is a StandaloneTool, and ${located.path} is not in src/standalone: " +
                    'a standalone tool is compiled without the classes of the project. ' +
                    'Move it to src/standalone/groovy, or implement ApplicationTool to keep it here.')
        }
        if (application && located.folder == ToolSource.Folder.STANDALONE) {
            error(source, classNode, "${name} is an ApplicationTool, and ${located.path} is in src/standalone, " +
                    'which has neither the application nor its classes. Move it to src/cli/groovy, or implement StandaloneTool.')
        }
        if (application && ToolDescriptorWriter.startsApplication(classNode) == null) {
            error(source, classNode, "startsApplication of ${name} has to be the constant true or false, " +
                    'where it is declared or in every constructor: the build reads it without running the tool.')
        }
    }

    /**
     * Warns of each deprecated type the class implements itself, as it is written in its source.
     *
     * @param classNode a class
     * @param located where its source is
     * @param source the source
     */
    static void warnOfDeprecatedTypes(ClassNode classNode, ToolSource located, SourceUnit source) {
        if (located.exempt) {
            return
        }
        for (String type : deprecatedTypes(classNode)) {
            warning(source, classNode, "${classNode.nameWithoutPackage} implements ${type}, which is deprecated: " +
                    'implement ApplicationTool (GrailsApplicationCommand: ApplicationTool, ToolSupport). Nothing else changes.')
        }
    }

    /**
     * @param classNode a tool class
     * @param located where its source is
     * @return what is deprecated about how the tool is written, for its descriptor: that the class
     * implements a deprecated type itself, and that its source is in the deprecated folder
     */
    static List<String> deprecations(ClassNode classNode, ToolSource located) {
        List<String> deprecated = []
        if (!deprecatedTypes(classNode).isEmpty()) {
            deprecated << ToolDescriptor.DEPRECATED_TYPE
        }
        if (located.folder == ToolSource.Folder.LEGACY) {
            deprecated << ToolDescriptor.DEPRECATED_FOLDER
        }
        deprecated
    }

    // the deprecated types the class names in its own list of what it implements, as they are named
    private static List<String> deprecatedTypes(ClassNode classNode) {
        List<String> named = []
        for (ClassNode type : classNode.interfaces) {
            String deprecated = DEPRECATED_TYPES[type.name]
            if (deprecated != null) {
                named << deprecated
            }
        }
        named
    }

    private static boolean isMarked(ClassNode classNode, String marker) {
        ClassNode current = classNode
        while (current != null && current != ClassHelper.OBJECT_TYPE) {
            if (current.name == marker || current.allInterfaces.any { ClassNode type -> type.name == marker }) {
                return true
            }
            current = current.superClass
        }
        false
    }

    private static void error(SourceUnit source, ClassNode classNode, String message) {
        source.errorCollector.addError(new SyntaxErrorMessage(
                new SyntaxException(message, classNode.lineNumber, classNode.columnNumber), source), false)
    }

    // a warning of the compilation, which a build that does not show those, as Gradle, shows from the standard error
    private static void warning(SourceUnit source, ClassNode classNode, String message) {
        if (!WarningMessage.isRelevant(WarningMessage.LIKELY_ERRORS, source.configuration.warningLevel)) {
            return
        }
        source.errorCollector.addWarning(WarningMessage.LIKELY_ERRORS, message,
                Token.newString(classNode.name, classNode.lineNumber, classNode.columnNumber), source)
        System.err.println("${source.name}:${classNode.lineNumber}: warning: ${message}")
    }
}
