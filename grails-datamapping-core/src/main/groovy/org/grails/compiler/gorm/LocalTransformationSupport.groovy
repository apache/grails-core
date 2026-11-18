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

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.AnnotatedNode
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassNode

/**
 * Shared guard logic for local, annotation-driven AST transformations in this package whose
 * {@code visit(ASTNode[], SourceUnit)} entry point only applies when the visited node is a class
 * carrying one specific annotation type.
 *
 * <p>Used by {@link DirtyCheckTransformation}, {@link JpaGormEntityTransformation} and
 * {@link GormEntityTransformation}.
 */
@CompileStatic
class LocalTransformationSupport {

    private LocalTransformationSupport() {
    }

    /**
     * @param astNodes the array a local transform's {@code visit(ASTNode[], SourceUnit)} receives
     * @param expectedAnnotationType the annotation type this transform applies to
     * @return the annotated {@link ClassNode}, or {@code null} if this transform does not apply
     * (the annotation doesn't match, or the annotated node isn't a class)
     */
    static ClassNode resolveAnnotatedClassOrNull(ASTNode[] astNodes, ClassNode expectedAnnotationType) {
        // Groovy's local-transform dispatch always supplies [AnnotationNode, AnnotatedNode] here,
        // so these casts are trusted rather than defensively checked first: a shape mismatch would
        // throw ClassCastException from the cast itself, before any check could run anyway.
        AnnotationNode node = (AnnotationNode) astNodes[0]
        AnnotatedNode parent = (AnnotatedNode) astNodes[1]

        if (expectedAnnotationType != node.getClassNode() || !(parent instanceof ClassNode)) {
            return null
        }

        return (ClassNode) parent
    }
}
