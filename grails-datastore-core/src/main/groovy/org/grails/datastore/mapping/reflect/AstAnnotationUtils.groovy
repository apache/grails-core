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

package org.grails.datastore.mapping.reflect

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.AnnotatedNode
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.Expression

import java.lang.annotation.Annotation

/**
 * Utility methods for dealing with annotations in AST transforms
 *
 * @author Graeme Rocher
 * @since 6.1.2
 */
@CompileStatic
class AstAnnotationUtils {

    private static final Set<String> JUNIT_ANNOTATION_NAMES = new HashSet<String>(Arrays.asList("org.junit.jupiter.api.BeforeEach", "org.junit.jupiter.api.AfterEach"))

    static AnnotationNode findAnnotation(AnnotatedNode classNode, Class<?> type) {
        List<AnnotationNode> annotations = classNode.getAnnotations()
        return annotations == null ? null : findAnnotation(new ClassNode(type), annotations)
    }

    static AnnotationNode findAnnotation(AnnotatedNode annotationClassNode, List<AnnotationNode> annotations) {
        for (AnnotationNode annotation : annotations) {
            if (annotation.getClassNode().equals(annotationClassNode)) {
                return annotation
            }
        }
        return null
    }

    /**
     * Returns true if MethodNode is marked with annotationClass
     * @param methodNode A MethodNode to inspect
     * @param annotationClass an annotation to look for
     * @return true if classNode is marked with annotationClass, otherwise false
     */
    static boolean hasAnnotation(final MethodNode methodNode, final Class<? extends Annotation> annotationClass) {
        def classNode = new ClassNode(annotationClass)
        return hasAnnotation(methodNode, classNode)
    }

    static boolean hasAnnotation(MethodNode methodNode, ClassNode annotationClassNode) {
        return !methodNode.getAnnotations(annotationClassNode).isEmpty()
    }

    /**
     * Whether the method node has any JUnit annotations
     *
     * @param md The method node
     * @return True if it does
     */
    static boolean hasJunitAnnotation(MethodNode md) {
        for (AnnotationNode annotation in md.getAnnotations()) {
            if (JUNIT_ANNOTATION_NAMES.contains(annotation.getClassNode().getName())) {
                return true
            }
        }
        return false
    }

    /**
     * Returns true if MethodNode is marked with annotationClass
     * @param methodNode A MethodNode to inspect
     * @param annotationClass an annotation to look for
     * @return true if classNode is marked with annotationClass, otherwise false
     */
    static boolean hasAnnotation(final MethodNode methodNode, String annotationClassName) {
        List<AnnotationNode> annos = methodNode.getAnnotations()
        for (ann in annos) {
            if (ann.classNode.name == annotationClassName) return true
        }
        return false
    }

    static boolean hasAnnotation(List<AnnotationNode> annotationNodes, AnnotationNode annotationNode) {
        return annotationNodes.any() { AnnotationNode ann ->
            ann.classNode.equals(annotationNode.classNode)
        }
    }

    /**
     * @param classNode a ClassNode to search
     * @param annotationsToLookFor Annotations to look for
     * @return true if classNode is marked with any of the annotations in annotationsToLookFor
     */
    static boolean hasAnyAnnotations(final ClassNode classNode, final Class<? extends Annotation>... annotationsToLookFor) {
        for (Class<? extends Annotation> annotationClass : annotationsToLookFor) {
            if (hasAnnotation(classNode, annotationClass)) {
                return true
            }
        }
        return false
    }

    /**
     * Returns true if classNode is marked with annotationClass
     * @param classNode A ClassNode to inspect
     * @param annotationClass an annotation to look for
     * @return true if classNode is marked with annotationClass, otherwise false
     */
    static boolean hasAnnotation(final ClassNode classNode, final Class<? extends Annotation> annotationClass) {
        return !classNode.getAnnotations(new ClassNode(annotationClass)).isEmpty()
    }

    /**
     * Adds an annotation to the given class node or returns the existing annotation
     *
     * @param classNode The class node
     * @param annotationClass The annotation class
     */
    static AnnotationNode addAnnotationOrGetExisting(AnnotatedNode classNode, Class<? extends Annotation> annotationClass) {
        return addAnnotationOrGetExisting(classNode, annotationClass, Collections.<String, Object> emptyMap())
    }

    /**
     * Adds an annotation to the give nclass node if it doesn't already exist
     *
     * @param classNode The class node
     * @param annotationClass The annotation class
     */
    static void addAnnotationIfNecessary(AnnotatedNode classNode, Class<? extends Annotation> annotationClass) {
        addAnnotationOrGetExisting(classNode, annotationClass)
    }

    /**
     * Adds an annotation to the given class node or returns the existing annotation
     *
     * @param annotatedNode The class node
     * @param annotationClass The annotation class
     */
    static AnnotationNode addAnnotationOrGetExisting(AnnotatedNode annotatedNode, Class<? extends Annotation> annotationClass, Map<String, Object> members) {
        ClassNode annotationClassNode = ClassHelper.make(annotationClass)
        return addAnnotationOrGetExisting(annotatedNode, annotationClassNode, members)
    }

    static AnnotationNode addAnnotationOrGetExisting(AnnotatedNode annotatedNode, ClassNode annotationClassNode) {
        return addAnnotationOrGetExisting(annotatedNode, annotationClassNode, Collections.<String, Object> emptyMap())
    }

    static AnnotationNode addAnnotationOrGetExisting(AnnotatedNode annotatedNode, ClassNode annotationClassNode, Map<String, Object> members) {
        List<AnnotationNode> annotations = annotatedNode.getAnnotations()
        AnnotationNode annotationToAdd = new AnnotationNode(annotationClassNode)
        if (annotations.isEmpty()) {
            annotatedNode.addAnnotation(annotationToAdd)
        } else {
            AnnotationNode existing = findAnnotation(annotationClassNode, annotations)
            if (existing != null) {
                annotationToAdd = existing
            } else {
                annotatedNode.addAnnotation(annotationToAdd)
            }
        }

        if (members != null && !members.isEmpty()) {
            for (Map.Entry<String, Object> memberEntry : members.entrySet()) {
                Object value = memberEntry.getValue()
                annotationToAdd.setMember(memberEntry.getKey(), value instanceof Expression ? (Expression) value : new ConstantExpression(value))
            }
        }
        return annotationToAdd
    }
}
