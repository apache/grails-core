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
package grails.gsp.taglib.compiler;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.CodeVisitorSupport;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.MapEntryExpression;
import org.codehaus.groovy.ast.expr.MapExpression;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.expr.TupleExpression;

/**
 * Collects the names a page puts into its own binding with {@code <g:set var="...">}.
 *
 * <p>A page resolves a name against its binding before anything else, so a page that sets a variable
 * named after a tag library namespace means that variable rather than the namespace. The model a page
 * is rendered with is not visible when it is compiled, but what the page itself sets is: it compiles
 * into a call naming the tag and its attributes.
 *
 * @since 8.0.0
 */
final class PageBindingCollector extends CodeVisitorSupport {

    private static final String MARKUP_TAG_CALL = "invokeTag";
    private static final String SET_TAG = "set";
    private static final String VAR_ATTRIBUTE = "var";

    private final Set<String> names = new HashSet<>();

    private PageBindingCollector() {
    }

    /**
     * @param classNode the compiled page
     * @return the names the page sets, never {@code null}
     */
    static Set<String> collect(ClassNode classNode) {
        PageBindingCollector collector = new PageBindingCollector();
        for (MethodNode method : classNode.getMethods()) {
            if (method.getCode() != null) {
                method.getCode().visit(collector);
            }
        }
        return collector.names.isEmpty() ? Collections.emptySet() : collector.names;
    }

    @Override
    public void visitMethodCallExpression(MethodCallExpression call) {
        if (MARKUP_TAG_CALL.equals(call.getMethodAsString()) &&
                call.getArguments() instanceof TupleExpression tuple &&
                tuple.getExpressions().size() > 3 &&
                tuple.getExpression(0) instanceof ConstantExpression tagName &&
                SET_TAG.equals(tagName.getValue()) &&
                tuple.getExpression(3) instanceof MapExpression attrs) {
            addVariableName(attrs);
        }
        super.visitMethodCallExpression(call);
    }

    private void addVariableName(MapExpression attrs) {
        for (MapEntryExpression entry : attrs.getMapEntryExpressions()) {
            Expression key = entry.getKeyExpression();
            Expression value = entry.getValueExpression();
            if (key instanceof ConstantExpression name && VAR_ATTRIBUTE.equals(name.getValue()) &&
                    value instanceof ConstantExpression variable && variable.getValue() != null) {
                this.names.add(variable.getValue().toString());
            }
        }
    }
}
