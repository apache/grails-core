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
package org.grails.compiler.injection;

import org.apache.grails.common.compiler.GroovyTransformOrder;
import org.codehaus.groovy.GroovyBugError;
import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.expr.ClassExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.transform.ASTTransformation;
import org.codehaus.groovy.transform.GroovyASTTransformation;
import org.codehaus.groovy.transform.TransformWithPriority;

import java.util.Arrays;
import java.util.Map;

/**
 * The logic for the {@link grails.artefact.ApiDelegate} location transform.
 *
 * @author Graeme Rocher
 * @since 2.0
 */
@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
public class ApiDelegateTransformation implements ASTTransformation, TransformWithPriority {

    @Override
    public int priority() {
        return GroovyTransformOrder.API_DELEGATE_ORDER;
    }

    public void visit(ASTNode[] nodes, SourceUnit source) {
        if (nodes.length != 2 || !(nodes[0] instanceof AnnotationNode) || !(nodes[1] instanceof AnnotatedNode)) {
            throw new GroovyBugError("Internal error: expecting [AnnotationNode, AnnotatedNode] but got: " + Arrays.asList(nodes));
        }

        AnnotatedNode parent = (AnnotatedNode) nodes[1];
        AnnotationNode annotationNode = (AnnotationNode) nodes[0];

        if (parent instanceof FieldNode) {
            Expression value = annotationNode.getMember("value");
            FieldNode fieldNode = (FieldNode) parent;
            final ClassNode type = fieldNode.getType();
            final ClassNode owner = fieldNode.getOwner();
            ClassNode supportedType = owner;
            if (value instanceof ClassExpression) {
                supportedType = value.getType();
            }

            GrailsASTUtils.addDelegateInstanceMethods(supportedType, owner, type, new VariableExpression(fieldNode.getName()), resolveGenericsPlaceHolders(supportedType), isNoNullCheck(), isUseCompileStatic());
        }
    }

    protected boolean isNoNullCheck() {
        return true;
    }

    protected boolean isUseCompileStatic() {
        return true;
    }

    protected Map<String, ClassNode> resolveGenericsPlaceHolders(ClassNode classNode) {
        return null;
    }
}
