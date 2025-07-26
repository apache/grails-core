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
package org.grails.plugins.web.rest.transform

import groovy.transform.CompileStatic
import org.apache.groovy.ast.tools.AnnotatedNodeUtils
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.FieldNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.Parameter
import org.codehaus.groovy.ast.expr.ClassExpression
import org.codehaus.groovy.ast.expr.ListExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.ExpressionStatement
import org.codehaus.groovy.ast.stmt.ReturnStatement
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.transform.ASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation
import org.codehaus.groovy.transform.TransformWithPriority

import grails.rest.Link
import grails.rest.Linkable
import org.apache.grails.common.compiler.GroovyTransformOrder

import static java.lang.reflect.Modifier.PRIVATE
import static java.lang.reflect.Modifier.PUBLIC
import static java.lang.reflect.Modifier.TRANSIENT
import static org.grails.compiler.injection.GrailsASTUtils.ZERO_PARAMETERS

/**
 * Implementation of the {@link Linkable} transform
 *
 * @author Graeme Rocher
 * @since 2.3
 */
@CompileStatic
@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
class LinkableTransform implements ASTTransformation, TransformWithPriority {

    private static final ClassNode MY_TYPE = new ClassNode(Linkable)
    public static final String LINK_METHOD = "link"
    public static final String RESOURCE_LINKS_FIELD = '$resourceLinks'
    public static final String LINKS_METHOD = "links"

    public static void addLinkingMethods(ClassNode classNode) {
        def linksField = new FieldNode(RESOURCE_LINKS_FIELD, PRIVATE | TRANSIENT, new ClassNode(Set).getPlainNodeReference(), classNode, new ListExpression())
        classNode.addField(linksField)

        final resourceLinksVariable = new VariableExpression('$resourceLinks')
        if (classNode.getMethods(LINK_METHOD).isEmpty()) {
            final mapParameter = new Parameter(new ClassNode(Map), LINK_METHOD)
            final linkMethodBody = new BlockStatement()
            final linkArg = new MethodCallExpression(new ClassExpression(new ClassNode(Link)), "createLink", new VariableExpression(mapParameter))
            linkMethodBody.addStatement(new ExpressionStatement(new MethodCallExpression(resourceLinksVariable, "add", linkArg)))
            def linkMethod = new MethodNode(LINK_METHOD, PUBLIC, ClassHelper.VOID_TYPE, [mapParameter] as Parameter[], null, linkMethodBody)
            classNode.addMethod(linkMethod)
            AnnotatedNodeUtils.markAsGenerated(classNode, linkMethod)

            def linkParameter = new Parameter(new ClassNode(Link), LINK_METHOD)
            def linkMethod2 = new MethodNode(LINK_METHOD, PUBLIC, ClassHelper.VOID_TYPE, [linkParameter] as Parameter[], null, new ExpressionStatement(new MethodCallExpression(resourceLinksVariable, "add", new VariableExpression(linkParameter))))
            classNode.addMethod(linkMethod2)
            AnnotatedNodeUtils.markAsGenerated(classNode, linkMethod2)
        }
        if (classNode.getMethods(LINKS_METHOD).isEmpty()) {
            def linksMethod = new MethodNode(LINKS_METHOD, PUBLIC, new ClassNode(Collection), ZERO_PARAMETERS, null, new ReturnStatement(resourceLinksVariable))
            classNode.addMethod(linksMethod)
            AnnotatedNodeUtils.markAsGenerated(classNode, linksMethod)
        }
    }

    @Override
    void visit(ASTNode[] astNodes, SourceUnit source) {
        if (!(astNodes[0] instanceof AnnotationNode) || !(astNodes[1] instanceof ClassNode)) {
            throw new RuntimeException('Internal error: wrong types: $node.class / $parent.class')
        }

        ClassNode parent = (ClassNode) astNodes[1]
        AnnotationNode annotationNode = (AnnotationNode) astNodes[0]
        if (!MY_TYPE.equals(annotationNode.getClassNode())) {
            return
        }

        addLinkingMethods(parent)
    }

    @Override
    int priority() {
        GroovyTransformOrder.LINK_ORDER
    }
}
