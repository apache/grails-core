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
package org.grails.web.mapping

import org.codehaus.groovy.ast.ClassCodeVisitorSupport
import org.codehaus.groovy.ast.PropertyNode
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.stmt.ExpressionStatement
import org.codehaus.groovy.control.SourceUnit

class ResponseCodeUrlMappingVisitor extends ClassCodeVisitorSupport {

    boolean insideMapping = false
    List<String> responseCodes = []

    public void visitProperty(PropertyNode node) {
        if (node?.name == "mappings") {
            insideMapping = true
        }
        super.visitProperty(node)
        if (node?.name == "mappings") {
            insideMapping = false
        }
    }

    public void visitMethodCallExpression(MethodCallExpression call) {
        if (insideMapping && call.methodAsString =~ /^\d{3}$/ && !responseCodes.contains(call.methodAsString)) {
            responseCodes << call.methodAsString
        }
        super.visitMethodCallExpression(call)
    }

    public void visitExpressionStatement(ExpressionStatement statement) {
        super.visitExpressionStatement(statement)
    }

    @Override
    protected SourceUnit getSourceUnit() {
        return null;
    }
}
