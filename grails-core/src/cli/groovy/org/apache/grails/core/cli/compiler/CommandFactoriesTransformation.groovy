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

import java.util.regex.Pattern

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.ModuleNode
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.transform.ASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation
import org.codehaus.groovy.transform.TransformWithPriority

import org.apache.grails.common.compiler.GroovyTransformOrder
import org.grails.compiler.injection.FactoriesFileWriter
import org.grails.compiler.injection.GlobalGrailsClassInjectorTransformation
import org.grails.compiler.injection.GrailsASTUtils
import org.grails.io.support.GrailsResourceUtils
import org.grails.io.support.UrlResource

/**
 * A global transformation that registers compiled
 * {@link org.apache.grails.core.cli.ApplicationCommand} implementations in
 * {@code META-INF/grails-cli.factories}, keyed by the command contract's class name.
 *
 * The transform ships in the {@code grails-core-cli} artifact, so it is active exactly when a
 * command can compile — a class implementing the contract only resolves when the cli artifact is
 * on the compile classpath. Command registrations never touch {@code META-INF/grails.factories}.
 *
 * @since 8.0
 */
@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
@CompileStatic
class CommandFactoriesTransformation implements ASTTransformation, TransformWithPriority {

    public static final ClassNode APPLICATION_COMMAND_CLASS = ClassHelper.make('org.apache.grails.core.cli.ApplicationCommand')

    public static final ClassNode APPLICATION_COMMAND_TARGET_AWARE_CLASS = ClassHelper.make('org.apache.grails.core.cli.ApplicationCommandTargetAware')

    /** The command registration file written into the compilation target directory */
    public static final String CLI_FACTORIES_LOCATION = 'META-INF/grails-cli.factories'

    /** Hand-authored registrations merged into the generated file */
    protected static final List<String> SOURCE_CLI_FACTORIES_LOCATIONS = [
            'src/main/resources/META-INF/grails-cli.factories',
            'src/cli/resources/META-INF/grails-cli.factories',
    ].asImmutable()

    /**
     * Commands shipped in a companion cli artifact live in the {@code cli} source set
     * ({@code src/cli/groovy} or {@code src/cli/java}), which is not one of the standard
     * project-source locations recognised by {@link GrailsResourceUtils#isProjectSource}.
     */
    protected static final Pattern CLI_SOURCE_PATTERN =
            Pattern.compile('.+?[\\\\/]src[\\\\/]cli[\\\\/](groovy|java)[\\\\/].+?\\.(groovy|java)$')

    @Override
    int priority() {
        return GroovyTransformOrder.COMMAND_FACTORIES_ORDER
    }

    @Override
    void visit(ASTNode[] nodes, SourceUnit source) {
        ModuleNode ast = source.getAST()
        List<ClassNode> classes = new ArrayList<>(ast.getClasses())

        URL url = GrailsASTUtils.getSourceUrl(source)

        if (url == null) {
            return
        }
        if (!GrailsResourceUtils.isProjectSource(new UrlResource(url)) && !isCliSource(url)) {
            return
        }

        File compilationTargetDirectory = GlobalGrailsClassInjectorTransformation.resolveCompilationTargetDirectory(source)

        for (ClassNode classNode : classes) {
            if (shouldRegisterCommand(classNode)) {
                FactoriesFileWriter.updateFactoriesWithType(classNode, APPLICATION_COMMAND_CLASS,
                        compilationTargetDirectory, CLI_FACTORIES_LOCATION, SOURCE_CLI_FACTORIES_LOCATIONS)
            }
        }
    }

    protected static boolean shouldRegisterCommand(ClassNode classNode) {
        !classNode.allInterfaces.any { ClassNode interfaceNode ->
            interfaceNode.name == APPLICATION_COMMAND_TARGET_AWARE_CLASS.name
        }
    }

    protected static boolean isCliSource(URL url) {
        CLI_SOURCE_PATTERN.matcher(url.file).matches()
    }
}
