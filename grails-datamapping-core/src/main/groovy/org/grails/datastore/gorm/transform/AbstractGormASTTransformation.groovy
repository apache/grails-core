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
package org.grails.datastore.gorm.transform

import groovy.transform.CompilationUnitAware
import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.AnnotatedNode
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.transform.ASTTransformation
import org.codehaus.groovy.transform.AbstractASTTransformation

import org.grails.datastore.mapping.core.Ordered

/**
 * Abstract base class for GORM AST transformations
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
abstract class AbstractGormASTTransformation extends AbstractASTTransformation implements CompilationUnitAware, ASTTransformation, Ordered {

    CompilationUnit compilationUnit

    @Override
    final void visit(ASTNode[] astNodes, SourceUnit source) {
        if (!(astNodes[0] instanceof AnnotationNode) || !(astNodes[1] instanceof AnnotatedNode)) {
            throw new RuntimeException("Internal error: wrong types: ${astNodes[0].getClass()} / ${astNodes[1].getClass()}")
        }

        AnnotatedNode annotatedNode = (AnnotatedNode) astNodes[1]
        AnnotationNode annotationNode = (AnnotationNode) astNodes[0]

        if (!isValidAnnotation(annotationNode, annotatedNode)) {
            return
        }

        Object appliedMarker = getAppliedMarker()
        if (annotatedNode.getNodeMetaData(appliedMarker) == appliedMarker) {
            return
        }

        visit(source, annotationNode, annotatedNode)

        annotatedNode.putNodeMetaData(appliedMarker, appliedMarker)
    }

    protected boolean isValidAnnotation(AnnotationNode annotationNode, AnnotatedNode classNode) {
        return getAnnotationType().equals(annotationNode.getClassNode()) || !(classNode instanceof ClassNode)
    }

    abstract void visit(SourceUnit source, AnnotationNode annotationNode, AnnotatedNode annotatedNode)

    protected abstract ClassNode getAnnotationType()

    protected abstract Object getAppliedMarker()

    @Override
    int getOrder() {
        // this is overridden here to ensure every class gives a unique order to ensure deterministic behavior
        priority()
    }

    abstract int priority()
}
