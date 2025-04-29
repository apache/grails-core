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
import org.apache.grails.common.compiler.GroovyTransformOrder
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.ModuleNode
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.transform.ASTTransformation
import org.codehaus.groovy.transform.AbstractASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation

import jakarta.persistence.Entity
import org.codehaus.groovy.transform.TransformWithPriority

/**
 * Makes all entities annotated with @Entity JPA into GORM entities
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
@GroovyASTTransformation(phase = CompilePhase.SEMANTIC_ANALYSIS)
class GlobalJpaEntityTransform extends AbstractASTTransformation implements ASTTransformation, CompilationUnitAware, TransformWithPriority {

    CompilationUnit compilationUnit

    @Override
    void visit(ASTNode[] astNodes, SourceUnit source) {
        ModuleNode ast = source.getAST();
        List<ClassNode> classes = ast.getClasses();
        for (ClassNode aClass : classes) {
            visitClass(aClass, source)
        }
    }

    void visitClass(ClassNode classNode, SourceUnit source) {
        if(hasAnnotation(classNode, ClassHelper.make(Entity))) {
            def jpaEntityTransformation = new JpaGormEntityTransformation()
            jpaEntityTransformation.compilationUnit = compilationUnit
            jpaEntityTransformation.visit(classNode, source)
        }
    }

    @Override
    int priority() {
        GroovyTransformOrder.GLOBAL_JPA_ORDER
    }
}
