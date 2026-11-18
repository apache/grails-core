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

package org.grails.compiler.gorm

import groovy.transform.CompilationUnitAware
import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.transform.ASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation
import org.codehaus.groovy.transform.TransformWithPriority

import grails.gorm.dirty.checking.DirtyCheck
import org.apache.grails.common.compiler.GroovyTransformOrder

/**
 * Applies the DirtyCheck transformation
 *
 * @author Graeme Rocher
 * @since 2.0
 */
@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
class DirtyCheckTransformation implements ASTTransformation, CompilationUnitAware, TransformWithPriority {

    private static final ClassNode MY_TYPE = new ClassNode(DirtyCheck)

    CompilationUnit compilationUnit

    @Override
    @CompileStatic
    void visit(ASTNode[] astNodes, SourceUnit source) {
        ClassNode cNode = LocalTransformationSupport.resolveAnnotatedClassOrNull(astNodes, MY_TYPE)
        if (cNode == null) {
            return
        }

        def dirtyCheckingTransformer = new DirtyCheckingTransformer()
        dirtyCheckingTransformer.compilationUnit = compilationUnit
        dirtyCheckingTransformer.performInjection(source, cNode)
    }

    @Override
    int priority() {
        GroovyTransformOrder.DIRTY_CHECK_ORDER
    }
}
