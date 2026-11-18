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

import groovy.transform.CompileStatic
import groovy.transform.TypeChecked
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.AnnotatedNode
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.control.SourceUnit

import spock.lang.Specification

/**
 * {@code AbstractGormASTTransformation} is only ever exercised indirectly through its concrete
 * subclasses (e.g. {@code TenantTransform}, {@code RollbackTransform}), whose tests all drive the class
 * through its "normal" path: a matching annotation on a not-yet-visited node. The {@code final visit(ASTNode[],
 * SourceUnit)} template method it defines is itself the real, public entry point every subclass inherits
 * unchanged, so its own early-return branches - an annotation that doesn't match the subclass's declared
 * annotation type, and a node that was already visited once - can be tested directly against a minimal test
 * subclass without needing any concrete production subclass's full behavior.
 */
class AbstractGormASTTransformationSpec extends Specification {

    static class RecordingTransformation extends AbstractGormASTTransformation {

        static final Object APPLIED_MARKER = new Object()

        List<ASTNode> visitedNodes = []

        @Override
        void visit(SourceUnit source, AnnotationNode annotationNode, AnnotatedNode annotatedNode) {
            visitedNodes << annotatedNode
        }

        @Override
        protected ClassNode getAnnotationType() {
            ClassHelper.make(CompileStatic)
        }

        @Override
        protected Object getAppliedMarker() {
            APPLIED_MARKER
        }

        @Override
        int priority() {
            7
        }
    }

    private static ClassNode newTargetClassNode() {
        new ClassNode('com.example.RecordingTarget', 0, ClassHelper.OBJECT_TYPE)
    }

    void "visiting a matching, not-yet-applied node runs the subclass visit and marks the node as applied"() {
        given:
        RecordingTransformation transformation = new RecordingTransformation()
        ClassNode targetNode = newTargetClassNode()
        AnnotationNode matchingAnnotation = new AnnotationNode(ClassHelper.make(CompileStatic))

        when:
        transformation.visit([matchingAnnotation, targetNode] as ASTNode[], null)

        then:
        transformation.visitedNodes == [targetNode]
        targetNode.getNodeMetaData(RecordingTransformation.APPLIED_MARKER) == RecordingTransformation.APPLIED_MARKER
    }

    void "visiting a class node with an annotation that does not match the subclass's declared annotation type is a no-op"() {
        given:
        RecordingTransformation transformation = new RecordingTransformation()
        ClassNode targetNode = newTargetClassNode()
        AnnotationNode mismatchedAnnotation = new AnnotationNode(ClassHelper.make(TypeChecked))

        when:
        transformation.visit([mismatchedAnnotation, targetNode] as ASTNode[], null)

        then:
        transformation.visitedNodes.empty
        targetNode.getNodeMetaData(RecordingTransformation.APPLIED_MARKER) == null
    }

    void "visiting the same node a second time is a no-op because it is already marked as applied"() {
        given:
        RecordingTransformation transformation = new RecordingTransformation()
        ClassNode targetNode = newTargetClassNode()
        AnnotationNode matchingAnnotation = new AnnotationNode(ClassHelper.make(CompileStatic))

        when:
        transformation.visit([matchingAnnotation, targetNode] as ASTNode[], null)
        transformation.visit([matchingAnnotation, targetNode] as ASTNode[], null)

        then:
        transformation.visitedNodes.size() == 1
    }

    void "getOrder delegates to priority"() {
        given:
        RecordingTransformation transformation = new RecordingTransformation()

        expect:
        transformation.getOrder() == 7
    }
}
