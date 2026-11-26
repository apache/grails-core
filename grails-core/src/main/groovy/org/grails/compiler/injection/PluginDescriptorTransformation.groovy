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
package org.grails.compiler.injection

import groovy.transform.CompilationUnitAware
import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.Phases
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.transform.ASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation

import grails.plugins.metadata.GrailsPluginDescriptor
import org.apache.grails.plugin.descriptor.PluginDescriptorWriter

/**
 * Writes the descriptors of the plugins a Groovy compilation produces, beside its classes, once the
 * compilation has written them. A plugin is a concrete {@code grails.plugins.Plugin} subclass annotated
 * with {@link GrailsPluginDescriptor}; an annotated class that is not one fails the compilation.
 *
 * @see CompilerPluginDescriptors
 */
@CompileStatic
@GroovyASTTransformation
class PluginDescriptorTransformation implements ASTTransformation, CompilationUnitAware {

    private static final ClassNode DESCRIPTOR = ClassHelper.make(GrailsPluginDescriptor)
    private static final String PLUGIN_CLASS = 'grails.plugins.Plugin'

    CompilationUnit compilationUnit
    private final Set<File> scheduled = []

    @Override
    void visit(ASTNode[] nodes, SourceUnit source) {
        File target = source.configuration.targetDirectory
        if (source.class.name == 'org.codehaus.jdt.groovy.control.EclipseSourceUnit') {
            target = GroovyEclipseCompilationHelper.resolveEclipseCompilationTargetDirectory(source)
        }
        if (target == null) {
            return
        }
        boolean declaresPlugin = false
        for (ClassNode type : source.AST.classes) {
            if (type.getAnnotations(DESCRIPTOR).empty) {
                continue
            }
            if (type.abstract || type.interface || !GrailsASTUtils.isSubclassOf(type, PLUGIN_CLASS)) {
                source.addErrorAndContinue(new org.codehaus.groovy.syntax.SyntaxException(
                        "${type.name} is annotated with @GrailsPluginDescriptor, so it must be a concrete " +
                                "subclass of ${PLUGIN_CLASS}".toString(), type.lineNumber, type.columnNumber))
                continue
            }
            declaresPlugin = true
        }
        File imports = new File(target, PluginDescriptorWriter.PLUGINS_IMPORTS_PATH)
        if ((declaresPlugin || imports.isFile()) && scheduled.add(target)) {
            compilationUnit.addPhaseOperation({ SourceUnit unit ->
                if (unit.is(source)) {
                    CompilerPluginDescriptors.reconcile(target, null)
                }
            } as CompilationUnit.ISourceUnitOperation, Phases.OUTPUT)
        }
    }
}
