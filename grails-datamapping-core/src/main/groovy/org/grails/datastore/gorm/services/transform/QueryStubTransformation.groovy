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

package org.grails.datastore.gorm.services.transform

import groovy.transform.CompileStatic

import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.ModuleNode
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.ResolveVisitor
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.transform.ASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation

/**
 * Keeps a {@code @Query} whose value is a GString out of the Java stubs of a jointly compiled source set.
 *
 * The stub generator can only print a constant, so it wrote {@code @Query(value=null)}, which javac rejects.
 * The annotation has source retention and the service transformation still needs it, so this global
 * transformation sets it aside before the stubs are written and {@link #restoreQueries} puts it back, resolved,
 * when the service transformation starts.
 *
 * @since 7.0
 */
@CompileStatic
@GroovyASTTransformation(phase = CompilePhase.CONVERSION)
class QueryStubTransformation implements ASTTransformation {

    private static final String STASHED_QUERIES = QueryStubTransformation.name + '.queries'
    private static final List<String> QUERY_NAMES = ['Query', 'grails.gorm.services.Query']
    private static final List<String> SERVICE_NAMES = ['Service', 'grails.gorm.services.Service']

    @Override
    void visit(ASTNode[] nodes, SourceUnit source) {
        if (nodes.length == 0 || !(nodes[0] instanceof ModuleNode)) {
            return
        }
        for (ClassNode classNode : ((ModuleNode) nodes[0]).classes) {
            if (classNode.annotations.any { AnnotationNode annotation -> SERVICE_NAMES.contains(annotation.classNode.name) }) {
                for (MethodNode method : classNode.methods) {
                    stashNonConstantQueries(method)
                }
            }
        }
    }

    /**
     * Puts back the {@code @Query} annotations that were set aside on the methods of the given class.
     *
     * @return True if any was put back
     */
    static boolean restoreQueries(ClassNode classNode, SourceUnit sourceUnit, CompilationUnit compilationUnit) {
        boolean restored = false
        for (MethodNode method : classNode.methods) {
            List<AnnotationNode> stashed = (List<AnnotationNode>) method.getNodeMetaData(STASHED_QUERIES)
            if (stashed != null) {
                method.removeNodeMetaData(STASHED_QUERIES)
                stashed.each { AnnotationNode annotation -> method.addAnnotation(annotation) }
                restored = true
            }
        }
        if (restored && compilationUnit != null) {
            new ResolveVisitor(compilationUnit).startResolving(classNode, sourceUnit)
        }
        return restored
    }

    private static void stashNonConstantQueries(MethodNode method) {
        List<AnnotationNode> queries = method.annotations.findAll { AnnotationNode annotation ->
            QUERY_NAMES.contains(annotation.classNode.name) && isNonConstant(annotation.getMember('value'))
        }
        if (queries) {
            method.putNodeMetaData(STASHED_QUERIES, queries)
            method.annotations.removeAll(queries)
        }
    }

    private static boolean isNonConstant(Expression value) {
        return value != null && !(value instanceof ConstantExpression)
    }
}
