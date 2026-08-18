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

import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.transform.ASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation

/**
 * Local, annotation-driven transformation used only to test {@link AbstractMethodDecoratingTransformation}
 * through a genuine compilation. It simply renames the decorated method and dispatches straight to
 * it - no wrapping closure, no transaction/tenant semantics - so tests can assert purely on which
 * methods {@code weaveClassNode} chose to decorate.
 *
 * @see AbstractMethodDecoratingTransformationSpec
 */
@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
class TestMethodDecoratingTransformation extends AbstractMethodDecoratingTransformation implements ASTTransformation {

    private static final ClassNode MY_TYPE = ClassHelper.make(ApplyTestMethodDecorating)
    private static final Object APPLIED_MARKER = new Object()

    @Override
    protected ClassNode getAnnotationType() {
        MY_TYPE
    }

    @Override
    protected Object getAppliedMarker() {
        APPLIED_MARKER
    }

    @Override
    protected String getRenamedMethodPrefix() {
        '$test__'
    }

    @Override
    protected Expression buildDelegatingMethodCall(SourceUnit sourceUnit, AnnotationNode annotationNode, ClassNode classNode,
                                                     MethodNode methodNode, MethodCallExpression originalMethodCall, BlockStatement newMethodBody) {
        originalMethodCall
    }

    @Override
    int priority() {
        0
    }
}
