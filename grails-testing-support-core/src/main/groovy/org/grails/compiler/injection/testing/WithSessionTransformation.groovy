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
package org.grails.compiler.injection.testing

import grails.testing.mixin.integration.WithSession
import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.*
import org.codehaus.groovy.ast.expr.*
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.transform.ASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation
import org.grails.test.spock.WithSessionSpecExtension

/**
 * AST transformation for @WithSession annotation.
 * Registers the Spock extension for handling session binding.
 * 
 * @author Grails Team
 * @since 7.1
 */
@CompileStatic
@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
class WithSessionTransformation implements ASTTransformation {
    
    static final ClassNode MY_TYPE = new ClassNode(WithSession)
    private static final String SPEC_CLASS = "spock.lang.Specification"
    
    @Override
    void visit(ASTNode[] nodes, SourceUnit source) {
        if (!(nodes[0] instanceof AnnotationNode) || !(nodes[1] instanceof AnnotatedNode)) {
            throw new RuntimeException("Internal error: wrong types: ${nodes[0].getClass()} / ${nodes[1].getClass()}")
        }
        
        AnnotatedNode parent = (AnnotatedNode) nodes[1]
        AnnotationNode annotationNode = (AnnotationNode) nodes[0]
        
        if (MY_TYPE != annotationNode.classNode) {
            return
        }
        
        if (parent instanceof ClassNode) {
            ClassNode classNode = (ClassNode) parent
            
            // For Spock specifications, the extension will handle it
            if (isSubclassOf(classNode, SPEC_CLASS)) {
                // Extension is registered via META-INF/services
                return
            }
            
            // For JUnit tests, add a property to signal session binding
            addWithSessionProperty(classNode, annotationNode)
        } else if (parent instanceof MethodNode) {
            // For method-level annotations, we need to handle differently
            // This would require more complex transformation
            // For now, method-level annotations are handled by the Spock extension
        }
    }
    
    private void addWithSessionProperty(ClassNode classNode, AnnotationNode annotationNode) {
        // Extract datasources from annotation
        Expression datasourcesExpr = annotationNode.getMember("datasources")
        
        if (datasourcesExpr instanceof ListExpression) {
            ListExpression listExpr = (ListExpression) datasourcesExpr
            List<String> datasources = []
            
            for (Expression expr : listExpr.expressions) {
                if (expr instanceof ConstantExpression) {
                    datasources << expr.value.toString()
                }
            }
            
            // Add a static property for the datasources
            if (datasources) {
                classNode.addProperty(
                    "withSession",
                    Modifier.STATIC | Modifier.PUBLIC,
                    ClassHelper.make(List),
                    new ListExpression(datasources.collect { new ConstantExpression(it) })
                )
            } else {
                // Empty list means all datasources
                classNode.addProperty(
                    "withSession",
                    Modifier.STATIC | Modifier.PUBLIC,
                    ClassHelper.BOOLEAN_TYPE,
                    new ConstantExpression(true)
                )
            }
        } else {
            // No datasources specified means all datasources
            classNode.addProperty(
                "withSession",
                Modifier.STATIC | Modifier.PUBLIC,
                ClassHelper.BOOLEAN_TYPE,
                new ConstantExpression(true)
            )
        }
    }
    
    private boolean isSubclassOf(ClassNode classNode, String superClassName) {
        if (classNode == null) {
            return false
        }
        
        if (classNode.name == superClassName) {
            return true
        }
        
        return isSubclassOf(classNode.superClass, superClassName)
    }
}