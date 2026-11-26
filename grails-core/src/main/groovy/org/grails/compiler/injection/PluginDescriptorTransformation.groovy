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
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.Phases
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.transform.ASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation

import grails.plugins.metadata.GrailsPluginDescriptor
import org.apache.grails.plugin.descriptor.PluginDescriptorWriter

/**
 * Publishes descriptors beside the classes produced by a standalone Groovy compilation.
 * Builds with a dedicated packaging task disable this transform on that source set.
 */
@CompileStatic
@GroovyASTTransformation
class PluginDescriptorTransformation implements ASTTransformation, CompilationUnitAware {

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
        File imports = new File(target, PluginDescriptorWriter.PLUGINS_IMPORTS_PATH)
        boolean declaresPlugin = source.AST.classes.any { type ->
            type.nameWithoutPackage.endsWith('GrailsPlugin') ||
                    !type.getAnnotations(ClassHelper.make(GrailsPluginDescriptor)).empty
        }
        if ((declaresPlugin || imports.isFile()) && scheduled.add(target)) {
            compilationUnit.addPhaseOperation({ SourceUnit unit ->
                if (unit.is(source)) {
                    CompilerPluginDescriptors.reconcile(target, null, false)
                }
            } as CompilationUnit.ISourceUnitOperation, Phases.OUTPUT)
        }
    }
}
