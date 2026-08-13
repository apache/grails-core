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

import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.FieldNode

import spock.lang.Specification

/**
 * {@code LocalTransformationSupport.resolveAnnotatedClassOrNull} is the shared guard extracted
 * from {@link DirtyCheckTransformation} and {@link JpaGormEntityTransformation}'s
 * {@code visit(ASTNode[], SourceUnit)} entry points.
 */
class LocalTransformationSupportSpec extends Specification {

    private static final ClassNode ANNOTATION_TYPE = ClassHelper.make(Deprecated)
    private static final ClassNode OTHER_ANNOTATION_TYPE = ClassHelper.make(SuppressWarnings)

    void "resolves the annotated class when the annotation type matches and the node is a class"() {
        given:
        ClassNode targetClass = new ClassNode('com.example.Target', 0, ClassHelper.OBJECT_TYPE)
        AnnotationNode annotationNode = new AnnotationNode(ANNOTATION_TYPE)

        expect:
        LocalTransformationSupport.resolveAnnotatedClassOrNull([annotationNode, targetClass] as ASTNode[], ANNOTATION_TYPE) == targetClass
    }

    void "returns null when the annotation type does not match"() {
        given:
        ClassNode targetClass = new ClassNode('com.example.Target', 0, ClassHelper.OBJECT_TYPE)
        AnnotationNode annotationNode = new AnnotationNode(OTHER_ANNOTATION_TYPE)

        expect:
        LocalTransformationSupport.resolveAnnotatedClassOrNull([annotationNode, targetClass] as ASTNode[], ANNOTATION_TYPE) == null
    }

    void "returns null when the annotated node is not a class"() {
        given:
        ClassNode declaringClass = new ClassNode('com.example.Target', 0, ClassHelper.OBJECT_TYPE)
        FieldNode fieldNode = new FieldNode('someField', 0, ClassHelper.STRING_TYPE, declaringClass, null)
        AnnotationNode annotationNode = new AnnotationNode(ANNOTATION_TYPE)

        expect:
        LocalTransformationSupport.resolveAnnotatedClassOrNull([annotationNode, fieldNode] as ASTNode[], ANNOTATION_TYPE) == null
    }
}
